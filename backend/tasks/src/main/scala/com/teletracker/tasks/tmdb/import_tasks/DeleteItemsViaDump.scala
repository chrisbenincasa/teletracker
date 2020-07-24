package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemUpdater
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.model.GenericTmdbDumpFileRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import scala.util.control.NonFatal

// s3://teletracker-data-us-west-2/scrape-results/tmdb/2020-07-14/movie_ids_sorted-2020-07-14.json.gz

@JsonCodec
@GenArgParser
case class DeleteItemsArgs(
  dumpInput: URI,
  scrapeInput: URI,
  itemType: ItemType,
  limit: Int = -1,
  dryRun: Boolean = true)

class DeleteItemsViaDump @Inject()(
  sourceRetriever: SourceRetriever,
  @SingleThreaded scheduledExecutorService: ScheduledExecutorService,
  itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DeleteItemsArgs] {

  override protected def runInternal(): Unit = {
    val ids = sourceRetriever
      .getSourceStream(args.dumpInput)
      .flatMap(source => {
        try {
          new IngestJobParser()
            .stream[IdDumpInput](source.getLines())
            .collect {
              case Right(value) if value.`type` == args.itemType =>
                value.external_ids.collectFirst {
                  case EsExternalId(provider, id)
                      if ExternalSource
                        .fromString(provider) == ExternalSource.TheMovieDb =>
                    id.toInt -> value.id
                }
            }
            .flatten
            .toSet
        } finally {
          source.close()
        }
      })
      .toMap

    val allKnownIds = sourceRetriever
      .getSourceStream(args.scrapeInput)
      .flatMap(source => {
        try {
          new IngestJobParser()
            .stream[GenericTmdbDumpFileRow](source.getLines())
            .collect {
              case Right(value) => value.id
            }
            .toSet
        } finally {
          source.close()
        }
      })
      .toSet

    val deletedItems = ids
      .filterKeys(id => !allKnownIds.contains(id))

    logger.info(s"There are ${deletedItems.size} deleted items")

    if (!args.dryRun) {
      val limited = deletedItems.toList.safeTake(args.limit)

      logger.info(s"Deleting ${limited.size} items from store.")

      AsyncStream
        .fromIterable(limited)
        .delayedMapF(250 millis, scheduledExecutorService) {
          case (tmdbId, item) =>
            logger.info(s"Deleting ID = ${item} (tmdb ID = ${tmdbId})")
            itemUpdater.delete(item).map(_ => None).recover {
              case NonFatal(_) =>
                Some(item)
            }
        }
        .flatMapOption(identity)
        .delayedForeachF(250 millis, scheduledExecutorService)(item => {
          logger.info(s"Retrying id = ${item}")
          itemUpdater.delete(item).map(_ => {}).recover {
            case NonFatal(e) =>
              logger.error(s"Could not delete item ${item}", e)
          }
        })
        .await()
    }
  }
}

@JsonCodec
case class IdDumpInput(
  `type`: ItemType,
  external_ids: List[EsExternalId],
  id: UUID)
