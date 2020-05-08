package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch._
import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

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
