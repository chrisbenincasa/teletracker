package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.tasks.scraper.HuluCatalogDeltaIngestJob
import com.teletracker.tasks.util.ArgJsonInstances._
import com.teletracker.tasks.util.{SourceRetriever, TaskMessageHelper}
import com.teletracker.tasks.{scraper, TeletrackerTaskWithDefaultArgs}
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.net.URI
import java.time.LocalDate

object HuluDeltaLocator {
  def getKey(date: LocalDate) =
    s"scrape-results/hulu/$date/hulu_catalog_full.txt"
}

class HuluDeltaLocator @Inject()(
  publisher: SqsClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends TeletrackerTaskWithDefaultArgs {

  import HuluDeltaLocator._

  override protected def runInternal(args: Args): Unit = {
    val maxDayBack = args.valueOrDefault("maxDaysBack", 3)
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

    val previousDate = (1 to maxDayBack).toStream
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
        val message = TaskMessageHelper.forTask[HuluCatalogDeltaIngestJob](
          scraper.IngestDeltaJobArgs(
            snapshotBefore = new URI(
              "s3",
              teletrackerConfig.data.s3_bucket,
              "/" + getKey(value),
              null
            ),
            snapshotAfter = new URI(
              "s3",
              teletrackerConfig.data.s3_bucket,
              "/" + getKey(today),
              null
            )
          )
        )

        publisher
          .sendMessage(
            SendMessageRequest
              .builder()
              .messageBody(message.asJson.noSpaces)
              .queueUrl(
                teletrackerConfig.async.taskQueue.url
              )
              .build()
          )

      case None =>
        throw new RuntimeException(
          s"Cannot find a valid before-delta snapshot after going back ${maxDayBack} days"
        )
    }

  }
}
