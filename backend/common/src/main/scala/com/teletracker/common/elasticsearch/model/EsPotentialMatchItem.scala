package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.{
  PartialEsItem,
  ScrapeCatalogType,
  ScrapedCastMember,
  ScrapedCrewMember,
  ScrapedItem
}
import com.teletracker.common.util.HasId
import com.teletracker.common.util.json.circe._
import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.generic.extras._
import io.circe.generic.extras.semiauto.{
  deriveConfiguredDecoder,
  deriveConfiguredEncoder
}
import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Try

object EsPotentialMatchItem {
  implicit val codec: Codec[EsPotentialMatchItem] =
    io.circe.generic.semiauto.deriveCodec

  implicit val hasId: HasId.Aux[EsPotentialMatchItem, String] =
    new HasId[EsPotentialMatchItem] {
      override type Id = String
      override def id(x: EsPotentialMatchItem): String = x.id
      override def idString(x: EsPotentialMatchItem): String = id(x)
    }

  implicit val updateableEsItem: UpdateableEsItem.Aux[
    EsPotentialMatchItem,
    EsPotentialMatchItemUpdateView
  ] =
    new UpdateableEsItem[EsPotentialMatchItem] {
      override type Out = EsPotentialMatchItemUpdateView

      override def to(
        t: EsPotentialMatchItem
      ): EsPotentialMatchItemUpdateView = {
        EsPotentialMatchItemUpdateView(
          id = t.id,
          created_at = Some(t.created_at),
          state = Some(t.state),
          last_updated_at = Some(t.last_updated_at),
          last_state_change = Some(t.last_state_change),
          potential = Some(t.potential),
          scraped = Some(t.scraped),
          availability = t.availability
        )
      }
    }

  def id(
    esItemId: UUID,
    esExternalId: EsExternalId
  ): String = {
    s"${esItemId}__${esExternalId}"
  }

  object EsPotentialMatchItemId {
    def unapply(arg: String): Option[(UUID, EsExternalId)] = {
      val parts = arg.split("__", 2)
      if (parts.length == 2) {
        for {
          uuid <- Try(UUID.fromString(parts(0))).toOption
          exId <- Try(EsExternalId.parse(parts(1))).toOption
        } yield {
          uuid -> exId
        }
      } else {
        None
      }
    }
  }
}

@JsonCodec
case class EsPotentialMatchItem(
  id: String,
  created_at: OffsetDateTime,
  state: EsPotentialMatchState,
  last_updated_at: OffsetDateTime,
  last_state_change: OffsetDateTime,
  potential: PartialEsItem,
  scraped: EsGenericScrapedItem,
  availability: Option[List[EsAvailability]])

@JsonCodec
case class EsPotentialMatchItemUpdateView(
  id: String,
  created_at: Option[OffsetDateTime] = None,
  state: Option[EsPotentialMatchState] = None,
  last_updated_at: Option[OffsetDateTime] = None,
  last_state_change: Option[OffsetDateTime] = None,
  potential: Option[PartialEsItem] = None,
  scraped: Option[EsGenericScrapedItem] = None,
  availability: Option[List[EsAvailability]] = None)

@JsonCodec
case class EsGenericScrapedItem(
  `type`: ScrapeCatalogType,
  item: EsScrapedItem,
  raw: Json)

object EsScrapedItem {
  def fromAnyScrapedItem[T <: ScrapedItem](item: T): EsScrapedItem = {
    EsScrapedItem(
      availableDate = item.availableDate,
      title = item.title,
      releaseYear = item.releaseYear,
      network = item.network,
      status = item.status,
      externalId = item.externalId,
      description = item.description,
      itemType = item.itemType,
      url = item.url,
      posterImageUrl = item.posterImageUrl,
      cast = item.cast.map(
        _.map(
          c =>
            EsScrapedCastMember(name = c.name, order = c.order, role = c.role)
        )
      ),
      crew = item.crew.map(
        _.map(
          c =>
            EsScrapedCrewMember(name = c.name, order = c.order, role = c.role)
        )
      ),
      version = item.version
    )
  }
}

@ConfiguredJsonCodec
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
  override val posterImageUrl: Option[String],
  override val cast: Option[Seq[EsScrapedCastMember]],
  override val crew: Option[Seq[EsScrapedCrewMember]],
  override val version: Option[Long])
    extends ScrapedItem {
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
  override val category: Option[String] = None
}

@JsonCodec
case class EsScrapedCastMember(
  override val name: String,
  override val order: Option[Int],
  override val role: Option[String])
    extends ScrapedCastMember

@JsonCodec
case class EsScrapedCrewMember(
  override val name: String,
  override val order: Option[Int],
  override val role: Option[String])
    extends ScrapedCrewMember
