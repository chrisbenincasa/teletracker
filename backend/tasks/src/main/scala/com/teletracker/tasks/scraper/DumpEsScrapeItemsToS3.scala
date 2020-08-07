package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.ScrapeItemScroller
import com.teletracker.common.model.scraping.{ScrapeCatalogType, ScrapeItemType}
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.util.{FileRotator, SourceRetriever, SourceWriter}
import com.twitter.util.StorageUnit
import io.circe.generic.JsonCodec
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import java.net.URI
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext

@JsonCodec
@GenArgParser
case class DumpEsScrapeItemsToS3Args(
  scrapeItemType: ScrapeItemType,
  version: Long,
  outputPrefix: String,
  limit: Int = -1,
  dryRun: Boolean = true)

object DumpEsScrapeItemsToS3Args

class DumpEsScrapeItemsToS3 @Inject()(
  scrapeItemScroller: ScrapeItemScroller,
  teletrackerConfig: TeletrackerConfig,
  sourceWriter: SourceWriter,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DumpEsScrapeItemsToS3Args] {
  override protected def runInternal(): Unit = {
    val query = QueryBuilders
      .boolQuery()
      .must(QueryBuilders.termQuery("type", args.scrapeItemType.toString))
      .must(QueryBuilders.termQuery("version", args.version))

    val tmpDir =
      Files.createTempDirectory(s"${args.scrapeItemType}-${args.version}-dump")
    val rotator = FileRotator.everyNBytes(
      s"items-${args.version}.json",
      StorageUnit.fromMegabytes(10),
      Some(tmpDir.toAbsolutePath.toFile.getAbsolutePath)
    )

    scrapeItemScroller
      .start(query)
      .safeTake(args.limit)
      .withEffect(doc => {
        rotator.writeLine(doc.raw.noSpaces)
      })
      .force
      .await()

    rotator.finish()

    val base = s"s3://${teletrackerConfig.data.s3_bucket}/${args.outputPrefix}"
    sourceRetriever
      .getUriStream(tmpDir.toUri)
      .foreach(input => {
        val fileName = input.getPath.split("/").last
        if (args.dryRun) {
          logger.info(s"Would've written to: ${base}/${fileName}")
        } else {
          sourceWriter
            .writeFile(URI.create(s"${base}/${fileName}"), Paths.get(input))
        }
      })
  }
}
