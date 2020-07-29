package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.args.{ArgParser, GenArgParser}
import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
import com.teletracker.tasks.TeletrackerTaskRunner
import io.circe.Encoder
import io.circe.generic.JsonCodec
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import java.net.URI
import java.time.LocalDate
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait DeltaLocatorJobArgsLike {
  def maxDaysBack: Int
  def local: Boolean
  def seedDumpDate: Option[LocalDate]
}

@JsonCodec
@GenArgParser
case class DeltaLocatorJobArgs(
  maxDaysBack: Int = 3,
  local: Boolean = false,
  seedDumpDate: Option[LocalDate])
    extends DeltaLocatorJobArgsLike

object DeltaLocatorJobArgs

abstract class DeltaLocatorJob[_ArgsType <: DeltaLocatorJobArgsLike: ArgParser](
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext,
  enc: Encoder[_ArgsType])
    extends TypedTeletrackerTask[_ArgsType] {
  protected def defaultMaxDaysBack = 3

  override def preparseArgs(args: RawArgs): ArgsType =
    postParseArgs(
      args.parse[DeltaLocatorJobArgs].get
    )

  protected def postParseArgs(halfParsed: DeltaLocatorJobArgs): ArgsType

  override protected def runInternal(): Unit = {
    val seedDate = args.seedDumpDate.getOrElse(LocalDate.now())

    try {
      s3Client.getObject(
        GetObjectRequest
          .builder()
          .bucket(teletrackerConfig.data.s3_bucket)
          .key(getKey(seedDate))
          .build()
      )
    } catch {
      case _: NoSuchKeyException =>
        throw new RuntimeException(
          s"Could not find seed dump for date $seedDate at key ${getKey(seedDate)}"
        )
    }

    val previousDate = (1 to args.maxDaysBack).toStream
      .map(daysBack => {
        try {
          val date = seedDate.minusDays(daysBack)

          s3Client.getObject(
            GetObjectRequest
              .builder()
              .bucket(teletrackerConfig.data.s3_bucket)
              .key(getKey(date))
              .build()
          )

          Some(date)
        } catch {
          case _: NoSuchKeyException => None
        }
      })
      .find(_.isDefined)
      .flatten

    previousDate match {
      case Some(value) =>
        val snapshotBeforeLocation = new URI(
          "s3",
          teletrackerConfig.data.s3_bucket,
          "/" + getKey(value),
          null
        )
        val snapshotAfterLocation = new URI(
          "s3",
          teletrackerConfig.data.s3_bucket,
          "/" + getKey(seedDate),
          null
        )

        logger.info(
          s"Found before and after deltas, located at ${snapshotBeforeLocation} and ${snapshotAfterLocation}"
        )

        val (actualBeforeLocation, actualAfterLocation) =
          postProcessDeltas(
            snapshotBeforeLocation -> value,
            snapshotAfterLocation -> seedDate
          )

        if (actualBeforeLocation != snapshotBeforeLocation || actualAfterLocation != actualAfterLocation) {
          logger.info(
            s"Post processing changed before and after deltas, located at ${actualBeforeLocation} and ${actualAfterLocation}"
          )
        }

        val messages =
          makeTaskMessages(actualBeforeLocation, actualAfterLocation)

        if (!args.local) {
          messages.foreach(registerFollowupTask)
        } else {
          // FOR DEBUGGING
          messages.foreach(message => {
            TeletrackerTaskRunner.instance
              .runFromJsonArgs(message.clazz, message.args)
          })
        }

      case None =>
        throw new RuntimeException(
          s"Cannot find a valid before-delta snapshot after going back ${{
            args.maxDaysBack
          }} days"
        )
    }

  }

  protected def postProcessDeltas(
    snapshotBeforeLocation: (URI, LocalDate),
    snapshotAfterLocation: (URI, LocalDate)
  ): (URI, URI) = (snapshotBeforeLocation._1, snapshotAfterLocation._1)

  protected def getKey(today: LocalDate): String

  protected def makeTaskMessages(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI
  ): List[TeletrackerTaskQueueMessage]
}
