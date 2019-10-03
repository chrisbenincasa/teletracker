package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ThingRaw, ThingType}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessSuccess
}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.execution.SequentialFutures
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

sealed trait MatchMode[T <: ScrapedItem] {
  def lookup(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[(T, ThingRaw)], List[T])]
}

class TmdbLookup[T <: ScrapedItem](
  tmdbClient: TmdbClient,
  tmdbProcessor: TmdbEntityProcessor
)(implicit executionContext: ExecutionContext)
    extends MatchMode[T] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def lookup(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[(T, ThingRaw)], List[T])] = {
    SequentialFutures
      .serialize(items)(lookupSingle(_, args))
      .map(_.flatten)
      .map(results => {
        val (x, y) = results.partition(_.isLeft)
        x.flatMap(_.left.toOption) -> y.flatMap(_.right.toOption)
      })
  }

  private def lookupSingle(
    item: T,
    args: IngestJobArgsLike
  ): Future[Option[Either[(T, ThingRaw), T]]] = {
    val search = if (item.isMovie) {
      tmdbClient.searchMovies(item.title).map(_.results.map(Left(_)))
    } else if (item.isTvShow) {
      tmdbClient.searchTv(item.title).map(_.results.map(Right(_)))
    } else {
      Future.failed(new IllegalArgumentException)
    }

    val matcher = new ScrapedItemMatcher

    search
      .flatMap(results => {
        results
          .find(matcher.findMatch(_, item, args.titleMatchThreshold)) match {
          case Some(foundMatch) =>
            tmdbProcessor.handleWatchable(foundMatch).flatMap {
              case ProcessSuccess(_, thing: ThingRaw) =>
                logger.info(
                  s"Saved ${item.title} with thing ID = ${thing.id}"
                )

                Future.successful(Some(Left(item -> thing)))

              case ProcessSuccess(_, _) =>
                logger.error("Unexpected result")
                Future.successful(None)

              case ProcessFailure(error) =>
                logger.error("Error handling movie", error)
                Future.successful(None)
            }

          case None =>
            Future.successful(Some(Right(item)))
        }
      })
  }
}

class DbLookup[T <: ScrapedItem](
  thingsDb: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends MatchMode[T] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def lookup(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[(T, ThingRaw)], List[T])] = {
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

        matches -> missingItems
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

trait WithItemMatching[T <: ScrapedItem] {}
