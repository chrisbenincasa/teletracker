package com.teletracker.tasks.scraper.matching

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ThingRaw, ThingType}
import com.teletracker.common.util.Slug
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

class DbLookup[T <: ScrapedItem](
  thingsDb: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends MatchMode[T] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def lookup(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val (withReleaseYear, withoutReleaseYear) =
      items.partition(_.releaseYear.isDefined)

    if (withoutReleaseYear.nonEmpty) {
      logger.info(s"${withoutReleaseYear.size} things without release year")
    }

    val itemsBySlug = withReleaseYear
      .map(item => {
        Slug(item.title, item.releaseYear.get) -> item
      })
      .toMap

    val matchThingsByName = findMatchesViaExactTitle(withoutReleaseYear)

    val foundMoviesFut = lookupThingsBySlugsAndType(
      itemsBySlug.filter {
        case (_, item) => item.thingType == ThingType.Movie
      },
      ThingType.Movie,
      args
    )

    val foundShowsFut = lookupThingsBySlugsAndType(
      itemsBySlug.filter {
        case (_, item) => item.thingType == ThingType.Show
      },
      ThingType.Show,
      args
    )

    val matchedThingsWithSlugs = for {
      foundMovies <- foundMoviesFut
      foundShow <- foundShowsFut
    } yield {
      foundMovies ++ foundShow
    }

    val fallbackMatchesFromSlug = matchedThingsWithSlugs
      .map(_.collect {
        case (scrapedItem, None) => scrapedItem
      })
      .flatMap(findMatchesViaExactTitle)

    val allMatchedItems = for {
      match1 <- matchThingsByName
      match2 <- matchedThingsWithSlugs.map(_.collect {
        case (scrapedItem, Some(thing)) => scrapedItem -> thing
      })
      match3 <- fallbackMatchesFromSlug
    } yield {
      match1 ++ match2 ++ match3
    }

    allMatchedItems.map {
      case matches =>
        val matchedTitles = matches.map(_._1).map(_.title).toSet
        val missingItems = items
          .filter(item => !matchedTitles.contains(item.title))

        if (missingItems.nonEmpty) {
          logger.info(
            s"Could not find matches for items: ${missingItems.map(_.title)}. Falling back to custom lookups"
          )
        }

        matches.map {
          case (scrapedItem, dbItem) =>
            MatchResult(scrapedItem, dbItem.id, dbItem.name)
        } -> missingItems
    }
  }

  private def lookupThingsBySlugsAndType(
    itemsBySlug: Map[Slug, T],
    thingType: ThingType,
    args: IngestJobArgsLike
  ): Future[List[(T, Option[ThingRaw])]] = {
    thingsDb
      .findThingsBySlugsRaw(itemsBySlug.keySet, Some(thingType))
      .flatMap(thingBySlug => {
        val missingSlugs = itemsBySlug.keySet -- thingBySlug.keySet

        val extraThingsFut = if (missingSlugs.nonEmpty) {
          val yearVarianceSlugsMap = missingSlugs.toList
            .collect {
              case slug @ Slug(title, Some(year)) =>
                Map(
                  Slug.raw(s"$title-${year - 1}") -> slug,
                  Slug.raw(s"$title-${year + 1}") -> slug
                )
            }
            .fold(Map.empty)(_ ++ _)

          thingsDb
            .findThingsBySlugsRaw(yearVarianceSlugsMap.keySet)
            .map(yearVarianceSlugsMap -> _)
        } else {
          Future.successful((Map.empty[Slug, Slug], Map.empty[Slug, ThingRaw]))
        }

        extraThingsFut.map(extraThingsAndVarianceMap => {
          val (varianceMap, extraThings) = extraThingsAndVarianceMap
          val variantSlugsByOriginalSlug = flipMap(varianceMap)

          val allThingsBySlug = extraThings ++ thingBySlug

          itemsBySlug.toList.sortBy(_._1.value).flatMap {
            case (itemSlug, scrapedItem) =>
              val matchedThing = allThingsBySlug
                .get(itemSlug)
                .orElse {
                  variantSlugsByOriginalSlug
                    .getOrElse(itemSlug, Set.empty)
                    .flatMap(allThingsBySlug.get)
                    .headOption
                }

              if (matchedThing.isEmpty) {
                logger.debug(
                  s"Could not match thing with slug: ${itemSlug}, falling back to exact title match"
                )
                Some(scrapedItem -> None)
              } else {
                matchedThing.flatMap(thingRaw => {
                  val dist = LevenshteinDistance.getDefaultInstance
                    .apply(
                      thingRaw.name.toLowerCase().trim,
                      scrapedItem.title.toLowerCase().trim
                    )

                  if (dist > args.titleMatchThreshold) {
                    logger.info(
                      s"Bad match for ${scrapedItem.title} => ${thingRaw.name} - DIST: $dist"
                    )

                    None
                  } else {
                    Some(scrapedItem -> Some(thingRaw))
                  }
                })
              }
          }
        })
      })
  }

  private def findMatchesViaExactTitle(
    scrapedItems: List[T]
  ): Future[List[(T, ThingRaw)]] = {
    thingsDb
      .findThingsByNames(scrapedItems.map(_.title).toSet)
      .map(thingsByName => {
        scrapedItems.map(itemInQuestion => {
          thingsByName
            .getOrElse(itemInQuestion.title.toLowerCase, Seq()) match {
            case Seq() => None

            case Seq(one) if one.`type` == itemInQuestion.thingType =>
              logger
                .info(s"Matched ${itemInQuestion.title} by name to ${one.id}")
              Some(itemInQuestion -> one)

            case many =>
              val matchingByType =
                many.filter(_.`type` == itemInQuestion.thingType)
              if (matchingByType.size == 1) {
                Some(itemInQuestion -> matchingByType.head)
              } else {
                logger.debug(
                  s"Found many things with the name = ${itemInQuestion.title}: ${many
                    .map(_.id)}"
                )
                None
              }
          }
        })
      })
      .map(_.flatten)
  }

  private def flipMap[K, V](m: Map[K, V]): Map[V, Set[K]] = {
    m.toList
      .map {
        case (k, v) => v -> k
      }
      .groupBy(_._1)
      .map {
        case (v, k) => v -> k.map(_._2).toSet
      }
  }
}
