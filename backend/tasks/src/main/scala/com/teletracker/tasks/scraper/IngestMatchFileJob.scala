package com.teletracker.tasks.scraper
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.matching.{DirectLookupMethod, LookupMethod}
import com.teletracker.tasks.scraper.model.MatchInput
import com.teletracker.tasks.scraper.netflix.NetflixScrapedCatalogItem
import io.circe.Codec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client

abstract class IngestMatchFileJob[T <: ScrapedItem: Codec]
    extends IngestJob[MatchInput[T]] {
  @Inject private[this] var directLookupMethod: DirectLookupMethod = _

  override protected def parseMode: IngestJobParser.ParseMode =
    IngestJobParser.JsonPerLine

  // Csst is safe because we know scrapedItem is not accessed

  override protected def lookupMethod(
    args: TypedArgs
  ): LookupMethod[MatchInput[T]] =
    directLookupMethod.asInstanceOf[LookupMethod[MatchInput[T]]]
}

class IngestNetflixCatalogMatchFile @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestMatchFileJob[NetflixScrapedCatalogItem] {
  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Netflix)
}
