//package com.teletracker.tasks.scraper.hulu
//
//import com.teletracker.common.config.TeletrackerConfig
//import com.teletracker.tasks.scraper.{DeltaLocateAndRunJob, DeltaLocatorJobArgs}
//import com.teletracker.tasks.util.ArgJsonInstances._
//import javax.inject.Inject
//import software.amazon.awssdk.services.s3.S3Client
//import java.time.LocalDate
//import scala.concurrent.ExecutionContext
//
//object HuluDeltaLocator {
//  def getKey(date: LocalDate) =
//    s"scrape-results/hulu/$date/${date}_hulu-catalog.all.json"
//}
//
//class LocateAndRunHuluCatalogDelta @Inject()(
//  s3Client: S3Client,
//  teletrackerConfig: TeletrackerConfig
//)(implicit executionContext: ExecutionContext)
//    extends DeltaLocateAndRunJob[
//      DeltaLocatorJobArgs,
//      HuluCatalogDeltaIngestJob
//    ](
//      s3Client,
//      teletrackerConfig
//    ) {
//
//  override protected def postParseArgs(
//    halfParsed: DeltaLocatorJobArgs
//  ): DeltaLocatorJobArgs = identity(halfParsed)
//
//  override protected def getKey(today: LocalDate): String =
//    HuluDeltaLocator.getKey(today)
//}
