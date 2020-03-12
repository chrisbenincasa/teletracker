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
import org.slf4j.LoggerFactory
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

@JsonCodec
case class DeltaLocatorJobArgs(
  maxDaysBack: Int,
  local: Boolean)

abstract class DeltaLocatorJob(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {

  override type TypedArgs = DeltaLocatorJobArgs

  implicit override val typedArgsEncoder: Encoder[DeltaLocatorJobArgs] =
    io.circe.generic.semiauto.deriveEncoder

  protected def defaultMaxDaysBack = 3

  override def preparseArgs(args: Args): DeltaLocatorJobArgs =
    DeltaLocatorJobArgs(
      maxDaysBack = args.valueOrDefault("maxDaysBack", defaultMaxDaysBack),
      local = args.valueOrDefault("local", false)
    )

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)
    val today = LocalDate.now()

    try {
      s3Client.getObject(
        GetObjectRequest
          .builder()
          .bucket(teletrackerConfig.data.s3_bucket)
          .key(getKey(today))
          .build()
      )
    } catch {
      case _: NoSuchKeyException =>
        throw new RuntimeException(
          s"Could not find seed dump for date $today at key ${getKey(today)}"
        )
    }

    val previousDate = (1 to parsedArgs.maxDaysBack).toStream
      .map(daysBack => {
        try {
          val date = today.minusDays(daysBack)

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
          "/" + getKey(today),
          null
        )

        logger.info(
          s"Found before and after deltas, located at ${snapshotBeforeLocation} and ${snapshotAfterLocation}"
        )

        val (actualBeforeLocation, actualAfterLocation) =
          postProcessDeltas(
            snapshotBeforeLocation -> value,
            snapshotAfterLocation -> today
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
              .runFromJson(message.clazz, message.args)
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

abstract class DeltaLocateAndRunJob[T <: IngestDeltaJob[_]: ClassTag](
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#TypedArgs],
  executionContext: ExecutionContext)
    extends DeltaLocatorJob(
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
