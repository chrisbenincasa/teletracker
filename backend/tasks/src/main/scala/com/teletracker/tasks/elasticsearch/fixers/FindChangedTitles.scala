package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.StringListOrString
import com.teletracker.common.model.tmdb.{Movie, TvShow}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Slug
import com.teletracker.tasks.model.{EsBulkUpdate, EsItemDumpRow}
import com.teletracker.tasks.scraper.IngestJobParser
import io.circe.syntax._
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import javax.inject.Inject
import java.net.URI
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class FindChangedTitles @Inject()(
  sourceRetriever: SourceRetriever,
  parser: IngestJobParser,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val dumpInput = rawArgs.valueOrThrow[URI]("dumpInput")
    val tmdbInput = rawArgs.valueOrThrow[URI]("tmdbInput")
    val output = rawArgs.valueOrThrow[String]("output")
    val append = rawArgs.valueOrDefault[Boolean]("append", false)
    val itemType = rawArgs.valueOrDefault[ItemType]("itemType", ItemType.Movie)

    val fileRotator = FileRotator.everyNBytes(
      "updates",
      StorageUnit.fromMegabytes(10),
      Some(output),
      append = append
    )

    val existingTitlesByTmdbId = readDumpToMap(dumpInput, itemType)

    def streamType(source: Source): Map[String, String] = {
      (itemType match {
        case ItemType.Movie =>
          parser
            .stream[Movie](source.getLines())
            .collect {
              case Right(value)
                  if value.title.isDefined && existingTitlesByTmdbId
                    .get(value.id.toString)
                    .exists(savedTitle => value.title.get != savedTitle._2) =>
                value
            }
            .foldLeft(Map.empty[String, String]) {
              case (acc, movie) =>
                acc + (movie.id.toString -> movie.title.get)
            }

        case ItemType.Show =>
          parser
            .stream[TvShow](source.getLines())
            .collect {
              case Right(value)
                  if existingTitlesByTmdbId
                    .get(value.id.toString)
                    .exists(savedTitle => value.name != savedTitle._2) =>
                value
            }
            .foldLeft(Map.empty[String, String]) {
              case (acc, show) =>
                acc + (show.id.toString -> show.name)
            }

        case ItemType.Person => throw new IllegalArgumentException
      })
    }

    val titlesThatChanged = sourceRetriever
      .getSourceAsyncStream(tmdbInput)
      .mapConcurrent(8)(source => {
        Future {
          try {
            streamType(source)
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(Map.empty[String, String])(_ ++ _)
      .await()

    println(existingTitlesByTmdbId.size)
    println(titlesThatChanged.size)

    val tmdbIdsValuesToUpdate =
      (existingTitlesByTmdbId.keySet.intersect(titlesThatChanged.keySet))

    tmdbIdsValuesToUpdate.foreach(
      id => {
        val existingItem = existingTitlesByTmdbId(id)
        val newTitle = titlesThatChanged(id)
        val update = EsBulkUpdate(
          teletrackerConfig.elasticsearch.items_index_name,
          existingItem._1,
          Map(
            "doc" -> Map(
              "title" -> StringListOrString.forString(newTitle).asJson,
              "slug" -> existingItem._3
                .flatMap(Slug.applyOptional(newTitle, _))
                .asJson
            ).asJson
          ).asJson.deepDropNullValues.noSpaces
        )
        println(s"Existing item: ${existingItem}, new title: ${newTitle}")

        fileRotator.writeLines(update.lines)
      }
    )

    fileRotator.finish()
  }

  private def readDumpToMap(
    dumpLoc: URI,
    itemType: ItemType
  ) = {
    sourceRetriever
      .getSourceAsyncStream(dumpLoc)
      .mapConcurrent(8)(source => {
        Future {
          try {
            parser
              .stream[EsItemDumpRow](source.getLines())
              .collect {
                case Right(item) if item._source.`type` == itemType =>
                  item
              }
              .foldLeft(Map.empty[String, (UUID, String, Option[Int])]) {
                case (map, item) =>
                  item._source.externalIdsGrouped
                    .get(ExternalSource.TheMovieDb) match {
                    case Some(value) =>
                      map + (value -> (item._source.id, item._source.title.get.head, item._source.release_date
                        .map(_.getYear)))
                    case None => map
                  }
              }
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(Map.empty[String, (UUID, String, Option[Int])])(_ ++ _)
      .await()
  }
}
