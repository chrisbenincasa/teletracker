package com.teletracker.tasks.scraper

import com.teletracker.common.tasks.{TaskMessageHelper, TeletrackerTask}
import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.util.SourceRetriever
import com.teletracker.tasks.TeletrackerTaskRunner
import io.circe.Encoder
import io.circe.generic.JsonCodec
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
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
case class DeltaLocatorJobArgs(
  maxDaysBack: Int,
  local: Boolean,
  seedDumpDate: Option[LocalDate] = None)
    extends DeltaLocatorJobArgsLike

abstract class DeltaLocatorJob[ArgsType <: DeltaLocatorJobArgsLike](
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext,
  enc: Encoder[ArgsType])
    extends TeletrackerTask {

  override type TypedArgs = ArgsType

  implicit override val typedArgsEncoder: Encoder[ArgsType] =
    enc

  protected def defaultMaxDaysBack = 3

  override def preparseArgs(args: Args): ArgsType =
    postParseArgs(
      DeltaLocatorJobArgs(
        maxDaysBack = args.valueOrDefault("maxDaysBack", defaultMaxDaysBack),
        local = args.valueOrDefault("local", false),
        seedDumpDate = args.value[LocalDate]("seedDumpDate")
      )
    )

  protected def postParseArgs(halfParsed: DeltaLocatorJobArgs): ArgsType

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)
    val seedDate = parsedArgs.seedDumpDate.getOrElse(LocalDate.now())

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

    val previousDate = (1 to parsedArgs.maxDaysBack).toStream
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
          makeTaskMessages(actualBeforeLocation, actualAfterLocation, args)

        if (!parsedArgs.local) {
          val queue = new SqsQueue[TeletrackerTaskQueueMessage](
            publisher,
            teletrackerConfig.async.taskQueue.url
          )

          queue.batchQueue(messages).await()
        } else {
          // FOR DEBUGGING
          messages.foreach(message => {
            TeletrackerTaskRunner.instance
              .runFromJsonArgs(message.clazz, message.args)
          })
        }

      case None =>
        throw new RuntimeException(
          s"Cannot find a valid before-delta snapshot after going back ${{ parsedArgs.maxDaysBack }} days"
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
    snapshotAfterLocation: URI,
    args: Args
  ): List[TeletrackerTaskQueueMessage]
}

abstract class DeltaLocateAndRunJob[
  ArgsType <: DeltaLocatorJobArgsLike,
  T <: IngestDeltaJob[_]: ClassTag
](
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#TypedArgs],
  executionContext: ExecutionContext,
  argsEnc: Encoder[ArgsType])
    extends DeltaLocatorJob[ArgsType](
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    ) {
  override protected def makeTaskMessages(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI,
    args: Args
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[T](
      IngestDeltaJobArgs(
        snapshotAfter = snapshotAfterLocation,
        snapshotBefore = snapshotBeforeLocation
      )
    ) :: Nil
  }
}
