package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{
  EsItemImage,
  EsOrdering,
  StringListOrString
}
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{Movie, TvShow}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Slug
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import io.circe.syntax._
import javax.inject.Inject
import java.net.URI
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class FindChangedImages @Inject()(
  sourceRetriever: SourceRetriever,
  parser: IngestJobParser,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val dumpInput = args.valueOrThrow[URI]("dumpInput")
    val tmdbInput = args.valueOrThrow[URI]("tmdbInput")
    val output = args.valueOrThrow[String]("output")
    val append = args.valueOrDefault[Boolean]("append", false)
    val itemType = args.valueOrDefault[ItemType]("itemType", ItemType.Movie)

    val fileRotator = FileRotator.everyNLinesOrSize(
      "updates",
      2000,
      StorageUnit.fromMegabytes(10),
      Some(output),
      append = append
    )

    val imagesByTmdbId = readDumpToMap(dumpInput, itemType)

    def streamType(source: Source): Map[String, List[EsItemImage]] = {
      (itemType match {
        case ItemType.Movie =>
          val toEsItem = implicitly[ToEsItem[Movie]]
          parser
            .stream[Movie](source.getLines())
            .collect {
              case Right(value)
                  if value.title.isDefined && imagesByTmdbId
                    .get(value.id.toString)
                    .exists(
                      savedTitle =>
                        toEsItem
                          .esItemImages(value)
                          .sorted(EsOrdering.forEsImages) != savedTitle._2
                    ) =>
                value
            }
            .foldLeft(Map.empty[String, List[EsItemImage]]) {
              case (acc, movie) =>
                acc + (movie.id.toString -> toEsItem
                  .esItemImages(movie)
                  .sorted(EsOrdering.forEsImages))
            }

        case ItemType.Show =>
          val toEsItem = implicitly[ToEsItem[TvShow]]
          parser
            .stream[TvShow](source.getLines())
            .collect {
              case Right(value)
                  if imagesByTmdbId
                    .get(value.id.toString)
                    .exists(
                      savedTitle =>
                        toEsItem
                          .esItemImages(value)
                          .sorted(EsOrdering.forEsImages) != savedTitle._2
                    ) =>
                value
            }
            .foldLeft(Map.empty[String, List[EsItemImage]]) {
              case (acc, show) =>
                acc + (show.id.toString -> toEsItem
                  .esItemImages(show)
                  .sorted(EsOrdering.forEsImages))
            }

        case ItemType.Person => throw new IllegalArgumentException
      })
    }

    val imagesThatChanged = sourceRetriever
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
      .foldLeft(Map.empty[String, List[EsItemImage]])(_ ++ _)
      .await()

    println(imagesByTmdbId.size)
    println(imagesThatChanged.size)

    val tmdbIdsValuesToUpdate =
      (imagesByTmdbId.keySet.intersect(imagesThatChanged.keySet))

    tmdbIdsValuesToUpdate.foreach(
      id => {
        val existingItem = imagesByTmdbId(id)
        val newImages = imagesThatChanged(id)
        val update = EsBulkUpdate(
          teletrackerConfig.elasticsearch.items_index_name,
          existingItem._1,
          Map(
            "doc" -> Map(
              "images" -> newImages.asJson
            ).asJson
          ).asJson.deepDropNullValues.noSpaces
        )
        println(s"Existing item: ${existingItem}, new images: ${newImages}")

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
              .foldLeft(Map.empty[String, (UUID, List[EsItemImage])]) {
                case (map, item) =>
                  item._source.externalIdsGrouped
                    .get(ExternalSource.TheMovieDb) match {
                    case Some(value) =>
                      map + (value -> (item._source.id, item._source.images
                        .getOrElse(Nil)
                        .sorted(EsOrdering.forEsImages)))
                    case None => map
                  }
              }
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(Map.empty[String, (UUID, List[EsItemImage])])(_ ++ _)
      .await()
  }
}
