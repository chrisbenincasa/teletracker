package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.{
  ExternalSource,
  ThingType,
  UserThingTag,
  UserThingTagType
}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID
import scala.util.Try
import scala.xml.parsing.ExternalSources

object StringListOrString {
  def forString(value: String): StringListOrString = new StringListOrString {
    override def get: List[String] = List(value)
  }
}

trait StringListOrString {
  def get: List[String]
}

object ExtraDecoders {
  implicit val stringListOrStringCodec: Codec[StringListOrString] = Codec.from(
    Decoder.decodeString.either(Decoder.decodeArray[String]).map {
      case Left(value) =>
        new StringListOrString {
          override def get: List[String] = List(value)
        }
      case Right(value) =>
        new StringListOrString {
          override def get: List[String] = value.toList
        }
    },
    Encoder.encodeList[String].contramap[StringListOrString](_.get)
  )
}

import ExtraDecoders._

object EsExternalId {
  final private val SEPARATOR = "__"

  implicit val esExternalIdCodec: Codec[EsExternalId] = Codec.from(
    Decoder.decodeString.map(EsExternalId.parse),
    Encoder.encodeString.contramap[EsExternalId](_.toString)
  )

  def apply(
    externalSources: ExternalSource,
    id: String
  ): EsExternalId = new EsExternalId(externalSources.getName, id)

  def parse(value: String): EsExternalId = {
    val Array(provider, id) = value.split(SEPARATOR, 2)
    EsExternalId(provider, id)
  }
}

case class EsExternalId(
  provider: String,
  id: String) {
  override def toString: String = {
    s"${provider}${EsExternalId.SEPARATOR}$id"
  }
}

@JsonCodec
case class EsItem(
  adult: Option[Boolean],
  availability: Option[List[EsAvailability]],
  cast: Option[List[EsItemCastMember]],
  crew: Option[List[EsItemCrewMember]],
  external_ids: Option[List[EsExternalId]],
  genres: Option[List[EsGenre]],
  id: UUID,
  images: Option[List[EsItemImage]],
  original_title: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  ratings: Option[List[EsItemRating]],
  recommendations: Option[List[EsItemRecommendation]],
  release_date: Option[LocalDate],
  release_dates: Option[List[EsItemReleaseDate]],
  runtime: Option[Int],
  slug: Slug,
  tags: Option[List[EsItemTag]],
  title: StringListOrString,
  `type`: ThingType) {

  def ratingsGrouped: Map[ExternalSource, EsItemRating] = {
    ratings
      .getOrElse(Nil)
      .map(rating => {
        ExternalSource.fromString(rating.provider_shortname) -> rating
      })
      .toMap
  }

  def imagesGrouped: Map[(ExternalSource, EsImageType), EsItemImage] = {
    images
      .getOrElse(Nil)
      .groupBy(
        image =>
          ExternalSource
            .fromString(image.provider_shortname) -> image.image_type
      )
      .map {
        case (k, v) => k -> v.head
      }
  }

  def scopeToUser(userId: Option[String]): EsItem = {
    userId match {
      case Some(value) =>
        val scopedTags = tags.map(_.filter(_.tag.startsWith(value)))
        copy(tags = scopedTags)

      case None => clearUserScopedData
    }
  }

  def clearUserScopedData: EsItem = {
    copy(tags = None)
  }
}

@JsonCodec
case class EsPerson(
  adult: Option[Boolean],
  biography: Option[String],
  birthday: Option[LocalDate],
  cast_credits: Option[List[EsPersonCastCredit]],
  crew_credits: Option[List[EsPersonCrewCredit]],
  external_ids: Option[List[EsExternalId]],
  deathday: Option[LocalDate],
  homepage: Option[String],
  id: UUID,
  images: Option[List[EsItemImage]],
  name: Option[String],
  place_of_birth: Option[String],
  popularity: Option[Double],
  slug: Slug,
  known_for: Option[List[EsDenormalizedItem]])

object EsItemTag {
  import io.circe.generic.semiauto._
  implicit val esItemTagCodec: Codec[EsItemTag] = deriveCodec

  final val SEPARATOR = "__"

  object TagFormatter {
    def format(
      userId: String,
      tag: UserThingTagType
    ): String = {
      s"${userId}${SEPARATOR}${tag}"
    }
  }

  def userScoped(
    userId: String,
    tag: UserThingTagType,
    value: Option[Double]
  ): EsItemTag = {
    EsItemTag(TagFormatter.format(userId, tag), value)
  }

  object UserScoped {
    def unapply(
      arg: EsItemTag
    ): Option[(String, UserThingTagType, Option[Double])] = {
      arg.tag.split(SEPARATOR, 2) match {
        case Array(userId, tag) =>
          Try(UserThingTagType.fromString(tag)).toOption
            .map(tagType => (userId, tagType, arg.value))
        case _ => None
      }
    }
  }
}

case class EsItemTag(
  tag: String,
  value: Option[Double])

@JsonCodec
case class EsItemReleaseDate(
  country_code: String,
  release_date: Option[LocalDate],
  certification: Option[String])

object EsAvailability {
  import io.circe.generic.semiauto._

  implicit val codec: Codec[EsAvailability] = deriveCodec

  def distinctFields(
    left: EsAvailability
  ): (Int, String, String, Option[Set[String]]) = {
    (
      left.network_id,
      left.region,
      left.offer_type,
      left.presentation_types.map(_.toSet)
    )
  }

  def availabilityEquivalent(
    left: EsAvailability,
    right: EsAvailability
  ): Boolean = {
    distinctFields(left) == distinctFields(right)
  }
}

@JsonCodec
case class EsAvailability(
  network_id: Int,
  region: String,
  start_date: Option[LocalDate],
  end_date: Option[LocalDate],
  offer_type: String,
  cost: Option[Double],
  currency: Option[String],
  presentation_types: Option[List[String]])

@JsonCodec
case class EsItemCastMember(
  character: Option[String],
  id: UUID,
  order: Int,
  name: String,
  slug: Slug)

@JsonCodec
case class EsPersonCastCredit(
  character: Option[String],
  id: UUID,
  title: String,
  `type`: ThingType,
  slug: Slug)

@JsonCodec
case class EsItemCrewMember(
  id: UUID,
  order: Option[Int],
  name: String,
  department: Option[String],
  job: Option[String],
  slug: Slug)

@JsonCodec
case class EsPersonCrewCredit(
  id: UUID,
  title: String,
  department: Option[String],
  job: Option[String],
  `type`: ThingType,
  slug: Slug)

@JsonCodec
case class EsGenre(
  id: Int,
  name: String)

@JsonCodec
case class EsItemImage(
  provider_id: Int,
  provider_shortname: String,
  id: String,
  image_type: EsImageType)

@JsonCodec
case class EsItemRecommendation(
  id: UUID,
  title: String,
  slug: Slug)
    extends EsDenormalizedItemLike

trait EsDenormalizedItemLike {
  def id: UUID
  def title: String
}

@JsonCodec
case class EsMinimalDenormalizedItem(
  id: UUID,
  title: String)
    extends EsDenormalizedItemLike

@JsonCodec
case class EsDenormalizedItem(
  id: UUID,
  title: String,
  `type`: String,
  slug: Slug)
    extends EsDenormalizedItemLike

@JsonCodec
case class EsItemRating(
  provider_id: Int,
  provider_shortname: String,
  vote_average: Double,
  vote_count: Option[Int])
