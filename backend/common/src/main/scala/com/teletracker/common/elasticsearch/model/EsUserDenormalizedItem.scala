package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

@JsonCodec
case class EsUserDenormalizedItem(
  id: UUID,
  alternative_titles: Option[List[EsItemAlternativeTitle]],
  availability: Option[List[EsAvailability]],
  cast: Option[List[EsItemCastMember]],
  crew: Option[List[EsItemCrewMember]],
  release_date: Option[LocalDate],
  genres: Option[List[EsGenre]],
  original_title: Option[String],
  popularity: Option[Double],
  ratings: Option[List[EsItemRating]],
  runtime: Option[Int],
  slug: Option[Slug],
  title: Option[String],
  `type`: ItemType)
