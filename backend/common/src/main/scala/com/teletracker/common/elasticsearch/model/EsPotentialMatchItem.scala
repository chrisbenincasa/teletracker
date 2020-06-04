package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.{
  PartialEsItem,
  ScrapeItemType,
  ScrapedItem
}
import com.teletracker.common.util.HasId
import com.teletracker.common.util.json.circe._
import io.circe.{Codec, Json}
import io.circe.generic.JsonCodec
import java.time.OffsetDateTime
import java.util.UUID

object EsPotentialMatchItem {
  implicit val codec: Codec[EsPotentialMatchItem] =
    io.circe.generic.semiauto.deriveCodec

  implicit val hasId: HasId.Aux[EsPotentialMatchItem, String] =
    new HasId[EsPotentialMatchItem] {
      override type Id = String
      override def id(x: EsPotentialMatchItem): String = x.id
      override def idString(x: EsPotentialMatchItem): String = id(x)
    }

  def id(
    esItemId: UUID,
    esExternalId: EsExternalId
  ): String = {
    s"${esItemId}__${esExternalId}"
  }
}

@JsonCodec
case class EsPotentialMatchItem(
  id: String,
  created_at: OffsetDateTime,
  state: EsPotentialMatchState,
  last_updated: OffsetDateTime,
  potential: PartialEsItem,
  scraped: EsGenericScrapedItem,
  availability: Option[List[EsAvailability]])

@JsonCodec
case class EsGenericScrapedItem(
  `type`: ScrapeItemType,
  item: EsScrapedItem,
  raw: Json)

object EsScrapedItem {
  def fromAnyScrapedItem[T <: ScrapedItem](item: T): EsScrapedItem = {
    require(item.thingType.isDefined)

    EsScrapedItem(
      availableDate = item.availableDate,
      title = item.title,
      releaseYear = item.releaseYear,
      network = item.network,
      status = item.status,
      externalId = item.externalId,
      description = item.description,
      itemType = item.thingType.get,
      url = item.url,
      posterImageUrl = item.posterImageUrl
    )
  }
}

@JsonCodec
case class EsScrapedItem(
  override val availableDate: Option[String],
  override val title: String,
  override val releaseYear: Option[Int],
  override val network: String,
  override val status: String,
  override val externalId: Option[String],
  override val description: Option[String],
  itemType: ItemType,
  override val url: Option[String],
  override val posterImageUrl: Option[String])
    extends ScrapedItem {
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
  override val category: Option[String] = None
}
