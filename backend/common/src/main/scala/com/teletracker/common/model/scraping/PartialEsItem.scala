package com.teletracker.common.model.scraping

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.{EsItem, EsItemImage}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import java.time.LocalDate
import java.util.UUID

case class PartialEsItem(
  id: UUID,
  description: Option[String],
  original_title: Option[String],
  title: String,
  release_date: Option[LocalDate],
  external_ids: Option[List[String]],
  `type`: ItemType,
  slug: Option[Slug],
  images: Option[List[EsItemImage]])

object PartialEsItem {
  implicit val codec: Codec[PartialEsItem] =
    io.circe.generic.semiauto.deriveCodec[PartialEsItem]

  def forEsItem(esItem: EsItem): PartialEsItem = {
    PartialEsItem(
      id = esItem.id,
      description = esItem.overview,
      original_title = esItem.original_title,
      title = esItem.title.get.head,
      release_date = esItem.release_date,
      external_ids = esItem.external_ids.map(_.map(_.toString)),
      `type` = esItem.`type`,
      slug = esItem.slug,
      images = esItem.images
    )
  }
}
