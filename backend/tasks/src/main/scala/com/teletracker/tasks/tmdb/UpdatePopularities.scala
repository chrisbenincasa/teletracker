package com.teletracker.tasks.tmdb

import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
import com.teletracker.common.aws.sqs.{SqsFifoQueue, SqsQueue}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.elasticsearch.{ItemLookup, PersonLookup}
import com.teletracker.common.pubsub.{
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestUpdate
}
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.model.{
  MovieDumpFileRow,
  PersonDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.TmdbDumpFileRow
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import javax.inject.Inject
import org.elasticsearch.common.io.stream.BytesStreamOutput
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class UpdatePopularitiesDependencies @Inject()(
  val sourceRetriever: SourceRetriever,
  val ingestJobParser: IngestJobParser,
  val itemLookup: ItemLookup,
  val personLookup: PersonLookup,
  val teletrackerConfig: TeletrackerConfig,
  val sqsAsyncClient: SqsAsyncClient)

class UpdateMoviePopularities @Inject()(
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdateItemPopularities[MovieDumpFileRow](
      ItemType.Movie,
      deps
    )

class UpdateTvShowPopularities @Inject()(
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdateItemPopularities[TvShowDumpFileRow](
      ItemType.Show,
      deps
    )

@JsonCodec
@GenArgParser
case class UpdatePopularitiesJobArgs(
  snapshotBefore: URI,
  snapshotAfter: URI,
  offset: Int,
  limit: Int,
  dryRun: Boolean,
  changeThreshold: Float,
  verbose: Boolean = false,
  mod: Option[Int],
  band: Option[Int])

object UpdatePopularitiesJobArgs

abstract class UpdateItemPopularities[T <: TmdbDumpFileRow: Decoder](
  itemType: ItemType,
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdatePopularities[T](itemType, deps) {
  override protected def lookupBatch(ids: List[Int]): Future[Map[Int, UUID]] = {
    deps.itemLookup
      .lookupItemsByExternalIds(
        ids
          .map(
            id => (ExternalSource.TheMovieDb, id.toString, itemType)
          )
      )
      .map(results => {
        results.map {
          case ((EsExternalId(_, id), _), item) => id.toInt -> item.id
        }
      })
  }

  override protected def createUpdateMessage(
    id: UUID,
    popularity: Double
  ): EsIngestMessage = {
    EsIngestMessage(
      EsIngestMessageOperation.Update,
      update = Some(
        EsIngestUpdate(
          index = deps.teletrackerConfig.elasticsearch.items_index_name,
          id.toString,
          Some(itemType),
          None,
          Some(Map("popularity" -> popularity).asJson)
        )
      )
    )
  }
}

class UpdatePeoplePopularities @Inject()(
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdatePopularities[PersonDumpFileRow](ItemType.Person, deps) {
  override protected def lookupBatch(ids: List[Int]): Future[Map[Int, UUID]] = {
    deps.personLookup
      .lookupPeopleByExternalIds(
        ids
          .map(_.toString)
          .map(EsExternalId(ExternalSource.TheMovieDb, _))
          .toSet
      )
      .map(results => {
        results.map {
          case (EsExternalId(id, _), item) => id.toInt -> item.id
        }
      })
  }

  override protected def createUpdateMessage(
    id: UUID,
    popularity: Double
  ): EsIngestMessage = {
    EsIngestMessage(
      EsIngestMessageOperation.Update,
      update = Some(
        EsIngestUpdate(
          index = deps.teletrackerConfig.elasticsearch.people_index_name,
          id.toString,
          None,
          None,
          Some(Map("popularity" -> popularity).asJson)
        )
      )
    )
  }
}

abstract class UpdatePopularities[T <: TmdbDumpFileRow: Decoder](
  itemType: ItemType,
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[UpdatePopularitiesJobArgs] {
  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _
  @Inject
  private[this] var esIngestQueue: SqsFifoQueue[EsIngestMessage] = _

  override def runInternal(): Unit = {
    if (args.band.isDefined && args.mod.isEmpty || args.band.isEmpty && args.mod.isDefined) {
      throw new IllegalArgumentException("Both mod and band must be defined")
    }

    val hasModAndBand = args.band.isDefined && args.mod.isDefined

    // Input must be SORTED BY POPULARITY DESC
    val beforeSource = deps.sourceRetriever.getSource(args.snapshotBefore)
    val afterSource = deps.sourceRetriever.getSource(args.snapshotAfter)

    val beforePopularitiesById = deps.ingestJobParser
      .stream[T](beforeSource.getLines())
      .applyIf(hasModAndBand)(
        _.valueFilterMod({
          case Right(value) => value.id
        }, args.mod.get, args.band.get)
      )
      .flatMap {
        case Left(value) =>
          logger.error("Could not parse line", value)
          None

        case Right(value) => Some(value.id -> value.popularity)
      }
      .toMap

    logger.info("Finished parsing before delta")

    val afterPopularitiesById = deps.ingestJobParser
      .stream[T](afterSource.getLines())
      .applyIf(hasModAndBand)(
        _.valueFilterMod({
          case Right(value) => value.id
        }, args.mod.get, args.band.get)
      )
      .flatMap {
        case Left(value) =>
          logger.error("Could not parse line", value)
          None

        case Right(value) => Some(value.id -> value.popularity)
      }
      .toMap

    logger.info("Finished parsing after delta")

    val afterSourceForUpdate =
      deps.sourceRetriever
        .getSource(args.snapshotAfter, consultCache = true)

    val processed = new AtomicInteger()

    deps.ingestJobParser
      .asyncStream[T](afterSourceForUpdate.getLines())
      .drop(args.offset)
      .safeTake(args.limit)
      .flatMapOption(_.toOption)
      .filter(line => afterPopularitiesById.isDefinedAt(line.id))
      .flatMapOption(item => {
        if (!beforePopularitiesById.isDefinedAt(item.id)) {
          Some(item.id -> afterPopularitiesById(item.id))
        } else {
          val afterPopularity = afterPopularitiesById(item.id)
          val beforePopularity = beforePopularitiesById(item.id)

          val delta = Math.abs(afterPopularity - beforePopularity)

          val meetsThreshold = delta >= args.changeThreshold

          if (args.verbose && meetsThreshold) {
            logger.info(
              s"Id = ${item.id} meets threshold with difference = ${afterPopularity - beforePopularity}. " +
                s"New popularity: ${afterPopularity}"
            )
          }

          if (meetsThreshold) {
            Some(item.id -> afterPopularitiesById(item.id))
          } else {
            None
          }
        }
      })
      .grouped(50)
      .foreachF(batch => {
        processed.addAndGet(batch.size)

        val output = new BytesStreamOutput()

        if (!args.dryRun) {
          AsyncStream
            .fromSeq(batch)
            .grouped(10)
            .mapF(innerBatch => {
              logger.debug(s"Looking up ${innerBatch.size} items")
              lookupBatch(innerBatch.map(_._1).toList).flatMap(itemIdById => {
                logger.debug(s"Found ${itemIdById.size} items")
                val messages = innerBatch
                  .flatMap {
                    case (tmdbId, popularity) =>
                      itemIdById.get(tmdbId).map(_ -> popularity)
                  }
                  .map {
                    case (uuid, popularity) =>
                      createUpdateMessage(uuid, popularity) -> uuid.toString
                  }

                esIngestQueue.batchQueue(messages.toList)
              })
            })
            .force
            .map(_ => {
              output.bytes().writeTo(Console.out)
            })
        } else {
          Future.unit
        }
      })
      .await()

    if (args.dryRun) {
      logger.info(s"Would've processed ${processed.get()} total popularities")
    } else {
      logger.info(s"Processed ${processed.get()} total popularities")
    }
  }

  protected def lookupBatch(ids: List[Int]): Future[Map[Int, UUID]]

  protected def createUpdateMessage(
    id: UUID,
    popularity: Double
  ): EsIngestMessage
}
