package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  UserThingTagType
}
import com.teletracker.common.elasticsearch.EsItemTag.TagFormatter
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.JsonCodec
import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.util.Try

object StringListOrString {
  implicit final val codec: Codec[StringListOrString] = Codec.from(
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

  def forString(value: String): StringListOrString = new StringListOrString {
    override def get: List[String] = List(value)
  }
}

trait StringListOrString {
  def get: List[String]
}

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
  alternative_titles: Option[List[EsItemAlternativeTitle]],
  availability: Option[List[EsAvailability]],
  cast: Option[List[EsItemCastMember]],
  crew: Option[List[EsItemCrewMember]],
  external_ids: Option[List[EsExternalId]],
  genres: Option[List[EsGenre]],
  id: UUID,
  images: Option[List[EsItemImage]],
  last_updated: Option[Long],
  original_title: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  ratings: Option[List[EsItemRating]],
  recommendations: Option[List[EsItemRecommendation]],
  release_date: Option[LocalDate],
  release_dates: Option[List[EsItemReleaseDate]],
  runtime: Option[Int],
  slug: Option[Slug],
  tags: Option[List[EsItemTag]],
  title: StringListOrString,
  `type`: ItemType) {

  def castMemberForId(id: UUID): Option[EsItemCastMember] =
    cast.flatMap(_.find(_.id == id))

  def containsCastMember(id: UUID): Boolean =
    castMemberForId(id).isDefined

  def crewMemberForId(id: UUID): Option[EsItemCrewMember] =
    crew.flatMap(_.find(_.id == id))

  def containsCrewMember(id: UUID): Boolean =
    crewMemberForId(id).isDefined

  def externalIdsGrouped: Map[ExternalSource, String] = {
    external_ids
      .getOrElse(Nil)
      .map(id => {
        ExternalSource.fromString(id.provider) -> id.id
      })
      .toMap
  }

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

  def toDenormalizedUserItem: EsUserDenormalizedItem = EsUserDenormalizedItem(
    id = id,
    release_date = release_date,
    genres = genres,
    original_title = original_title,
    popularity = popularity,
    slug = slug,
    `type` = `type`
  )
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
  slug: Option[Slug],
  known_for: Option[List[EsDenormalizedItem]]) {

  def externalIdsGrouped: Map[ExternalSource, String] = {
    external_ids
      .getOrElse(Nil)
      .map(id => {
        ExternalSource.fromString(id.provider) -> id.id
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
}

object EsItemTaggable {
  implicit val esItemTaggableString: EsItemTaggable[String] =
    new EsItemTaggable[String] {
      override def makeItemTag(
        tag: String,
        value: Option[String],
        lastUpdated: Instant
      ) = {
        EsItemTag(
          tag = tag,
          value = None,
          string_value = value,
          last_updated = Some(lastUpdated)
        )
      }

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[String],
        lastUpdated: Instant = Instant.now()
      ): EsUserItemTag = {
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = None,
          double_value = None,
          date_value = None,
          string_value = value,
          last_updated = Some(lastUpdated)
        )
      }
    }

  implicit val esItemTaggableDouble: EsItemTaggable[Double] = {
    new EsItemTaggable[Double] {
      override def makeItemTag(
        tag: String,
        value: Option[Double],
        lastUpdated: Instant
      ): EsItemTag = EsItemTag(
        tag = tag,
        value = value,
        string_value = None,
        last_updated = Some(lastUpdated)
      )

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[Double],
        lastUpdated: Instant
      ): EsUserItemTag =
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = None,
          double_value = value,
          date_value = None,
          string_value = None,
          last_updated = Some(lastUpdated)
        )
    }
  }

  implicit val esItemTaggableInt: EsItemTaggable[Int] =
    new EsItemTaggable[Int] {
      override def makeItemTag(
        tag: String,
        value: Option[Int],
        lastUpdated: Instant
      ): EsItemTag = {
        EsItemTag(
          tag = tag,
          value = value.map(_.toDouble),
          string_value = None,
          last_updated = Some(lastUpdated)
        )
      }

      override def makeUserItemTag(
        userId: String,
        itemId: UUID,
        tag: UserThingTagType,
        value: Option[Int],
        lastUpdated: Instant
      ): EsUserItemTag = {
        EsUserItemTag(
          tag = tag.toString,
          user_id = Some(userId),
          item_id = Some(itemId.toString),
          int_value = value,
          double_value = None,
          date_value = None,
          string_value = None,
          last_updated = Some(lastUpdated)
        )
      }
    }
}

trait EsItemTaggable[T] {
  def makeItemTag(
    tag: String,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ): EsItemTag

  def makeUserScopedTag(
    userId: String,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ) = {
    makeItemTag(TagFormatter.format(userId, tag), value, lastUpdated)
  }

  def makeUserItemTag(
    userId: String,
    itemId: UUID,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Instant = Instant.now()
  ): EsUserItemTag
}

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

//  def userScoped(
//    userId: String,
//    tag: UserThingTagType,
//    value: Option[Double],
//    lastUpdated: Option[Instant]
//  ): EsItemTag = {
//    EsItemTag(TagFormatter.format(userId, tag), value, None, lastUpdated)
//  }

  def userScopedString(
    userId: String,
    tag: UserThingTagType,
    value: Option[String],
    lastUpdated: Option[Instant]
  ): EsItemTag = {
    EsItemTag(TagFormatter.format(userId, tag), None, value, lastUpdated)
  }

  def userScoped[T](
    userId: String,
    tag: UserThingTagType,
    value: Option[T],
    lastUpdated: Option[Instant]
  )(implicit esItemTaggable: EsItemTaggable[T]
  ): EsItemTag = {
    esItemTaggable.makeUserScopedTag(
      userId,
      tag,
      value,
      lastUpdated.getOrElse(Instant.now())
    )
  }

  object UserScoped {
    def unapply(arg: EsItemTag): Option[
      (
        String,
        UserThingTagType,
        Option[Double],
        Option[String],
        Option[Instant]
      )
    ] = {
      arg.tag.split(SEPARATOR, 2) match {
        case Array(userId, tag) =>
          Try(UserThingTagType.fromString(tag)).toOption
            .map(
              tagType =>
                (userId, tagType, arg.value, arg.string_value, arg.last_updated)
            )
        case _ => None
      }
    }
  }
}

case class EsItemTag(
  tag: String,
  value: Option[Double],
  string_value: Option[String],
  last_updated: Option[Instant])

@JsonCodec
case class EsItemAlternativeTitle(
  country_code: String,
  title: String,
  `type`: Option[String])

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
  slug: Option[Slug])

@JsonCodec
case class EsPersonCastCredit(
  character: Option[String],
  id: UUID,
  title: String,
  `type`: ItemType,
  slug: Option[Slug])

@JsonCodec
case class EsItemCrewMember(
  id: UUID,
  order: Option[Int],
  name: String,
  department: Option[String],
  job: Option[String],
  slug: Option[Slug])

@JsonCodec
case class EsPersonCrewCredit(
  id: UUID,
  title: String,
  department: Option[String],
  job: Option[String],
  `type`: ItemType,
  slug: Option[Slug])

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
  slug: Option[Slug])
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

@JsonCodec
case class EsUserItem(
  id: String,
  item_id: Option[UUID],
  user_id: Option[String],
  tags: List[EsUserItemTag],
  item: Option[EsUserDenormalizedItem])

object EsUserItemTag {
  implicit val codec: Codec[EsUserItemTag] =
    io.circe.generic.semiauto.deriveCodec

  def noValue(tag: UserThingTagType): EsUserItemTag = {
    EsUserItemTag(tag = tag.toString, last_updated = Some(Instant.now()))
  }

  def value[T](
    userId: String,
    itemId: UUID,
    tag: UserThingTagType,
    value: Option[T]
  )(implicit esItemTaggable: EsItemTaggable[T]
  ): EsUserItemTag = esItemTaggable.makeUserItemTag(userId, itemId, tag, value)

  def forInt(
    tag: UserThingTagType,
    value: Int
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    int_value = Some(value),
    last_updated = Some(Instant.now())
  )

  def forDouble(
    tag: UserThingTagType,
    value: Double
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    double_value = Some(value),
    last_updated = Some(Instant.now())
  )

  def forString(
    tag: UserThingTagType,
    value: String
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    string_value = Some(value),
    last_updated = Some(Instant.now())
  )
}

@JsonCodec
case class EsUserItemTag(
  tag: String,
  user_id: Option[String] = None,
  item_id: Option[String] = None,
  int_value: Option[Int] = None,
  double_value: Option[Double] = None,
  date_value: Option[Instant] = None,
  string_value: Option[String] = None,
  last_updated: Option[Instant])

@JsonCodec
case class EsUserDenormalizedItem(
  id: UUID,
  release_date: Option[LocalDate],
  genres: Option[List[EsGenre]],
  original_title: Option[String],
  popularity: Option[Double],
  slug: Option[Slug],
  `type`: ItemType)
