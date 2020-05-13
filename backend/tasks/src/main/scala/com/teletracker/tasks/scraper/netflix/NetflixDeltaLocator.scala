package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.tasks.scraper.model.WhatsOnNetflixCatalogItem
import com.teletracker.tasks.scraper.{
  DeltaLocateAndRunJob,
  DeltaLocatorJobArgs,
  DeltaLocatorJobArgsLike,
  IngestJobParser
}
import com.teletracker.tasks.util.ArgJsonInstances._
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.io.{BufferedOutputStream, FileOutputStream, PrintStream}
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import scala.concurrent.ExecutionContext

object NetflixDeltaLocator {
  def getTvKey(date: LocalDate) =
    s"scrape-results/netflix/whats-on-netflix/$date/netflix-tv-catalog.json"

  def getConvertedTvKey(date: LocalDate) =
    s"scrape-results/netflix/whats-on-netflix/$date/netflix-tv-catalog-converted.json"
}

@JsonCodec
case class NetflixCatalogDeltaArgs(
  maxDaysBack: Int,
  local: Boolean,
  seedDumpDate: Option[LocalDate] = None)
    extends DeltaLocatorJobArgsLike

class LocateAndRunNetflixTvCatalogDelta @Inject()(
  sourceRetriever: SourceRetriever,
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig,
  sourceWriter: SourceWriter
)(implicit executionContext: ExecutionContext)
    extends DeltaLocateAndRunJob[
      NetflixCatalogDeltaArgs,
      NetflixCatalogDeltaIngestJob
    ](
      s3Client,
      teletrackerConfig
    ) {

  override protected def postParseArgs(
    halfParsed: DeltaLocatorJobArgs
  ): NetflixCatalogDeltaArgs = {
    NetflixCatalogDeltaArgs(
      maxDaysBack = halfParsed.maxDaysBack,
      local = halfParsed.local,
      seedDumpDate = halfParsed.seedDumpDate
    )
  }

  override protected def postProcessDeltas(
    snapshotBeforeLocation: (URI, LocalDate),
    snapshotAfterLocation: (URI, LocalDate)
  ): (URI, URI) = {
    convertAndWriteFile(snapshotBeforeLocation._1, snapshotBeforeLocation._2) -> convertAndWriteFile(
      snapshotAfterLocation._1,
      snapshotAfterLocation._2
    )
  }

  private def convertAndWriteFile(
    snapshotLocation: URI,
    snapshotDate: LocalDate
  ) = {
    val source = sourceRetriever.getSource(snapshotLocation)

    val convertedDestination = new URI(
      s"s3://${teletrackerConfig.data.s3_bucket}/${NetflixDeltaLocator
        .getConvertedTvKey(snapshotDate)}"
    )

    val tmp = Files.createTempFile("netflix_convert", "tmp")
    tmp.toFile.deleteOnExit()

    val writer = new PrintStream(
      new BufferedOutputStream(new FileOutputStream(tmp.toFile))
    )

    try {
      new IngestJobParser().parse[WhatsOnNetflixCatalogItem](
        source.getLines(),
        IngestJobParser.JsonPerLine
      ) match {
        case Left(value) =>
          throw value
        case Right(value) =>
          value
            .map(TransformWhatsOnNetflixCatalog.convert)
            .foreach(item => {
              writer.println(item.asJson.noSpaces)
            })
      }
    } finally {
      source.close()
    }

    sourceWriter.writeFile(convertedDestination, tmp)

    convertedDestination
  }

  override protected def getKey(today: LocalDate): String =
    NetflixDeltaLocator.getTvKey(today)
}
