package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingRaw
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

sealed trait MatchMode[T <: ScrapedItem] {
  def lookup(
    items: List[T],
    args: IngestJobArgsLike[T]
  ): Future[List[(T, ThingRaw)]]
}

class TmdbLookup[T <: ScrapedItem](
  tmdbClient: TmdbClient,
  tmdbProcessor: TmdbEntityProcessor
)(implicit executionContext: ExecutionContext)
    extends MatchMode[T] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def lookup(
    items: List[T],
    args: IngestJobArgsLike[T]
  ): Future[List[(T, ThingRaw)]] = {
    SequentialFutures.serialize(items)(lookupSingle(_, args)).map(_.flatten)
  }

  private def lookupSingle(
    item: T,
    args: IngestJobArgsLike[T]
  ): Future[Option[(T, ThingRaw)]] = {
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
          .find(matcher.findMatch(_, item, args.titleMatchThreshold))
          .map(x => tmdbProcessor.handleWatchable(x))
          .map(_.flatMap {
            case ProcessSuccess(_, thing: ThingRaw) =>
              logger.info(
                s"Saved ${item.title} with thing ID = ${thing.id}"
              )

              Future.successful(Some(item -> thing))

            case ProcessSuccess(_, _) =>
              logger.error("Unexpected result")
              Future.successful(None)

            case ProcessFailure(error) =>
              logger.error("Error handling movie", error)
              Future.successful(None)
          })
          .getOrElse(Future.successful(None))
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
    args: IngestJobArgsLike[T]
  ): Future[List[(T, ThingRaw)]] = {
    val (withReleaseYear, withoutReleaseYear) =
      items.partition(_.releaseYear.isDefined)

    if (withoutReleaseYear.nonEmpty) {
      println(s"${withoutReleaseYear.size} things without release year")
    }

    val itemsBySlug = withReleaseYear
      .map(item => {
        Slug(item.title, item.releaseYear.get.toInt) -> item
      })
      .toMap

    println(itemsBySlug)

    thingsDb
      .findThingsBySlugsRaw(itemsBySlug.keySet)
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
            case (itemSlug, item) =>
              val matchedThing = allThingsBySlug
                .get(itemSlug)
                .orElse {
                  variantSlugsByOriginalSlug
                    .getOrElse(itemSlug, Set.empty)
                    .flatMap(allThingsBySlug.get)
                    .headOption
                }

              if (matchedThing.isEmpty) {
                logger.info(s"Could not match thing with slug: ${itemSlug}")
              }

              matchedThing.flatMap(thingRaw => {
                val dist = LevenshteinDistance.getDefaultInstance
                  .apply(
                    thingRaw.name.toLowerCase().trim,
                    item.title.toLowerCase().trim
                  )

                if (dist > args.titleMatchThreshold) {
                  logger.info(
                    s"Bad match for ${item.title} => ${thingRaw.name} - DIST: $dist"
                  )

                  None
                } else {
                  Some(item -> thingRaw)
                }
              })
          }
        })
      })
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
      .toMap
  }
}

trait WithItemMatching[T <: ScrapedItem] {}
