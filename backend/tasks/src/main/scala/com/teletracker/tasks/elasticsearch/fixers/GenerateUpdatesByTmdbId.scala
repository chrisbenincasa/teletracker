package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import javax.inject.Inject
import java.net.URI
import io.circe.syntax._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class GenerateUpdatesByTmdbId @Inject()(
  sourceRetriever: SourceRetriever,
  parser: IngestJobParser,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val dumpLocation = args.valueOrThrow[URI]("dumpLocation")
    val tmdbToUpdateFile = args.valueOrThrow[URI]("updateFile")
    val itemType = args.valueOrThrow[ItemType]("itemType")
    val outputPath = args.valueOrThrow[String]("outputPath")
    val limit = args.valueOrDefault("limit", -1)

    val tmdbIdsToId = readDumpToMap(dumpLocation, itemType)

    val rotater = FileRotator.everyNBytes(
      "updates",
      StorageUnit.fromMegabytes(10),
      Some(outputPath)
    )

    sourceRetriever
      .getSourceStream(tmdbToUpdateFile)
      .foreach(source => {
        try {
          parser
            .stream[TmdbBackfillOutputRow](source.getLines())
            .collect {
              case Right(row @ TmdbBackfillOutputRow(tmdbId, _))
                  if tmdbIdsToId.isDefinedAt(tmdbId.toString) =>
                row
            }
            .safeTake(limit)
            .foreach(row => {
              val itemId = tmdbIdsToId(row.tmdbId.toString)
              rotater.writeLines(
                EsBulkUpdate(
                  teletrackerConfig.elasticsearch.items_index_name,
                  itemId,
                  Map("doc" -> row.partialJson).asJson.noSpaces
                ).lines
              )

              logger.info(
                s"Would've updated ${row} to id = ${itemId}"
              )
            })
        } finally {
          source.close()
        }
      })

    rotater.finish()
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
              .foldLeft(Map.empty[String, UUID]) {
                case (map, item) =>
                  item._source.externalIdsGrouped
                    .get(ExternalSource.TheMovieDb) match {
                    case Some(value) => map + (value -> item._source.id)
                    case None        => map
                  }
              }
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(Map.empty[String, UUID])(_ ++ _)
      .await()
  }
}
