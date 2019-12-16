package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.tasks.scraper.DeltaLocateAndRunJob
import com.teletracker.tasks.util.ArgJsonInstances._
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import java.time.LocalDate
import scala.concurrent.ExecutionContext

object HuluDeltaLocator {
  def getKey(date: LocalDate) =
    s"scrape-results/hulu/$date/hulu_catalog_full.txt"
}

class LocateAndRunHuluCatalogDelta @Inject()(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends DeltaLocateAndRunJob[HuluCatalogDeltaIngestJob](
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    ) {
  override protected def getKey(today: LocalDate): String =
    HuluDeltaLocator.getKey(today)
}
