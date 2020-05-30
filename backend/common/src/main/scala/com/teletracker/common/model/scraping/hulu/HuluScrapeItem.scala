package com.teletracker.common.model.scraping.hulu

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.json.circe._
import com.teletracker.common.model.scraping.ScrapedItem
import io.circe.generic.JsonCodec

@JsonCodec
case class HuluScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  notes: String,
  category: Option[String],
  network: String,
  status: String,
  externalId: Option[String],
  description: Option[String],
  `type`: ItemType)
    extends ScrapedItem {
  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean =
    !isMovie || category.getOrElse("").toLowerCase().contains("series")
}

@JsonCodec
case class HuluSearchResponse(groups: List[HuluSearchResponseGroup])

@JsonCodec
case class HuluSearchResponseGroup(results: List[HuluSearchResponseResult])

@JsonCodec
case class HuluSearchResponseResult(
  entity_metadata: Option[HuluSearchResultMetadata],
  metrics_info: Option[HuluSearchMetricsInfo])

@JsonCodec
case class HuluSearchResultMetadata(
  premiere_date: Option[String],
  target_name: String)

@JsonCodec
case class HuluSearchMetricsInfo(target_type: String)
