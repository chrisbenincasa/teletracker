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
  release_date: Option[LocalDate],
  genres: Option[List[EsGenre]],
  original_title: Option[String],
  popularity: Option[Double],
  slug: Option[Slug],
  `type`: ItemType)
