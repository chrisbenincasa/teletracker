package com.teletracker.tasks.scraper.disney

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchDirectLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.model.{
  DisneyPlusCatalogItem,
  PotentialInput
}
import com.teletracker.tasks.scraper.{IngestJobParser, IngestPotentialMatches}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client

class IngestDisneyPlusPotentialMatches @Inject()(
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache)
    extends IngestPotentialMatches[DisneyPlusCatalogItem] {

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.DisneyPlus)

  override protected def parseMode: IngestJobParser.ParseMode =
    IngestJobParser.JsonPerLine
}
