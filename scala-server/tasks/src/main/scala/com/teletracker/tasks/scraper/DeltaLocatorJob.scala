package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.util.{SourceRetriever, TaskMessageHelper}
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskRunner}
import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.syntax._
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.net.URI
import java.time.LocalDate
import scala.compat.java8.FutureConverters._
import scala.reflect.ClassTag

@JsonCodec
case class DeltaLocatorJobArgs(
  maxDaysBack: Int,
  local: Boolean)

abstract class DeltaLocatorJob(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
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
        throw new RuntimeException(s"Could not find seed dump for date $today")
    }

    val previousDate = (1 to parsedArgs.maxDaysBack).toStream
      .map(daysBack => {
        try {
          val date = today.minusDays(daysBack)

          s3Client.getObject(
            GetObjectRequest
              .builder()
              .bucket(teletrackerConfig.data.s3_bucket)
              .key(getKey(today))
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

        val message = makeTaskMessage(
          snapshotBeforeLocation,
          snapshotAfterLocation,
          args
        )
        if (!parsedArgs.local) {

          publisher
            .sendMessage(
              SendMessageRequest
                .builder()
                .messageBody(
                  message.asJson.noSpaces
                )
                .queueUrl(
                  teletrackerConfig.async.taskQueue.url
                )
                .build()
            )
            .toScala
            .await()
        } else {
          // FOR DEBUGGING
          TeletrackerTaskRunner.instance
            .runFromJson(message.clazz, message.args)
        }

      case None =>
        throw new RuntimeException(
          s"Cannot find a valid before-delta snapshot after going back ${{ parsedArgs.maxDaysBack }} days"
        )
    }

  }

  protected def getKey(today: LocalDate): String

  protected def makeTaskMessage(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI,
    args: Args
  ): TeletrackerTaskQueueMessage
}

abstract class DeltaLocateAndRunJob[T <: IngestDeltaJob[_]: ClassTag](
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#TypedArgs])
    extends DeltaLocatorJob(
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    ) {
  override protected def makeTaskMessage(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI,
    args: Args
  ): TeletrackerTaskQueueMessage = {
    TaskMessageHelper.forTask[T](
      IngestDeltaJobArgs(snapshotBeforeLocation, snapshotAfterLocation)
    )
  }
}
