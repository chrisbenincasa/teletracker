package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.auto._
import javax.inject.Inject

case class NetflixCatalogDeltaIngestJob @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess)
    extends IngestDeltaJob[UnogsNetflixCatalogItem] {

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: UnogsNetflixCatalogItem): String =
    item.externalId.get
}
