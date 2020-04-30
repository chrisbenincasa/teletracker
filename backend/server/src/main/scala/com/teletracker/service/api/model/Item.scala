package com.teletracker.service.api.model

import com.teletracker.common.db.model.{ItemType, UserThingTagType}
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  EsExternalId,
  EsGenre,
  EsItem,
  EsItemCrewMember,
  EsItemImage,
  EsItemRating,
  EsItemReleaseDate,
  EsItemTag,
  EsItemVideo,
  EsPerson
}
import com.teletracker.common.util.Slug
import io.circe.Codec
import io.circe.generic.JsonCodec
import java.time.{Instant, LocalDate}
import java.util.UUID
import com.teletracker.common.util.json.circe._

object Item {
  import io.circe.generic.semiauto._

  implicit val codec: Codec[Item] = deriveCodec

  def fromEsItem(
    esItem: EsItem,
    materializedRecommendations: List[Item] = Nil,
    additionalPeople: Map[UUID, EsPerson] = Map.empty
  ): Item = {
    Item(
      adult = esItem.adult,
      alternate_titles = esItem.title.get,
      availability = esItem.availability,
      cast = esItem.cast.map(_.map(member => {
        ItemCastMember(
          character = member.character,
          id = member.id,
          order = member.order,
          name = member.name,
          slug = member.slug,
          person = additionalPeople
            .get(member.id)
            .map(Person.fromEsPerson(_, None))
        )
      }).sortBy(_.order)),
      crew = esItem.crew,
      external_ids = esItem.external_ids
        .map(_.map(id => ItemExternalId(provider = id.provider, id = id.id))),
      genres = esItem.genres,
      id = esItem.id,
      images = esItem.images,
      original_title = esItem.original_title,
      overview = esItem.overview,
      popularity = esItem.popularity,
      ratings = esItem.ratings,
      recommendations = Some(materializedRecommendations),
      release_date = esItem.release_date,
      release_dates = esItem.release_dates,
      runtime = esItem.runtime,
      slug = esItem.slug,
      tags = esItem.tags.map(_.collect {
        case EsItemTag
              .UserScoped(userId, typ, value, stringValue, lastUpdated) =>
          ItemTag(Some(userId), typ, value, stringValue, lastUpdated)
      }),
      title = esItem.title.get.headOption,
      `type` = esItem.`type`,
      videos = esItem.videos
    )
  }
}

case class Item(
  adult: Option[Boolean],
  alternate_titles: List[String],
  availability: Option[List[EsAvailability]],
  cast: Option[List[ItemCastMember]],
  crew: Option[List[EsItemCrewMember]],
  external_ids: Option[List[ItemExternalId]],
  genres: Option[List[EsGenre]],
  id: UUID,
  images: Option[List[EsItemImage]],
  original_title: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  ratings: Option[List[EsItemRating]],
  recommendations: Option[List[Item]],
  release_date: Option[LocalDate],
  release_dates: Option[List[EsItemReleaseDate]],
  runtime: Option[Int],
  slug: Option[Slug],
  tags: Option[List[ItemTag]],
  title: Option[String],
  `type`: ItemType,
  videos: Option[List[EsItemVideo]]) {

  def scopeToUser(userId: String): Item = scopeToUser(Some(userId))

  def scopeToUser(userId: Option[String]): Item = {
    userId match {
      case Some(value) =>
        val scopedTags = tags.map(
          _.filter(tag => tag.userId.isEmpty || tag.userId.contains(value))
        )

        copy(tags = scopedTags)

      case None => clearUserScopedData
    }
  }

  def clearUserScopedData: Item = {
    copy(tags = None) // TODO: Only filter userId scoped tags
  }
}

@JsonCodec
case class ItemCastMember(
  character: Option[String],
  id: UUID,
  order: Int,
  name: String,
  slug: Option[Slug],
  person: Option[Person])

@JsonCodec
case class ItemTag(
  userId: Option[String],
  tag: UserThingTagType,
  value: Option[Double],
  string_value: Option[String],
  lastUpdated: Option[Instant])

@JsonCodec
case class ItemExternalId(
  provider: String,
  id: String)
