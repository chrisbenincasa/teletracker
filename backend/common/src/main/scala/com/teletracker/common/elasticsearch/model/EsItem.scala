package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.EsImageType
import com.teletracker.common.elasticsearch.model.EsAvailability.AvailabilityKey
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.util.{Sanitizers, Slug}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

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
  `type`: ItemType,
  videos: Option[List[EsItemVideo]]) {

  def usReleaseDateOrFallback: Option[LocalDate] = {
    release_dates
      .flatMap(_.find(_.country_code == "US").flatMap(_.release_date))
      .orElse(release_date)
  }

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

  def imagesGrouped: Map[(ExternalSource, EsImageType), List[EsItemImage]] = {
    images
      .getOrElse(Nil)
      .groupBy(
        image =>
          ExternalSource
            .fromString(image.provider_shortname) -> image.image_type
      )
      .mapValues(_.sortBy(_.id))
      .toMap
  }

  def videosGrouped: Map[ExternalSource, List[EsItemVideo]] = {
    videos
      .getOrElse(Nil)
      .groupBy(video => ExternalSource.fromString(video.provider_shortname))
  }

  def sanitizedTitle: String =
    Sanitizers.normalizeQuotes(title.get.head)

  def availabilityGrouped: Map[Int, List[EsAvailability]] = {
    availability.getOrElse(Nil).groupBy(_.network_id).toMap
  }

  def availabilityByKey: Map[AvailabilityKey, EsAvailability] = {
    availability.getOrElse(Nil).map(av => EsAvailability.getKey(av) -> av).toMap
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

  def toDenormalizedUserItem: EsUserDenormalizedItem =
    EsUserDenormalizedItem(
      id = id,
      alternative_titles = alternative_titles,
      availability = availability,
      cast = cast,
      crew = crew,
      release_date = release_date,
      genres = genres,
      original_title = original_title,
      popularity = popularity,
      ratings = ratings,
      runtime = runtime,
      slug = slug,
      title = title.get.headOption,
      `type` = `type`
    )
}
