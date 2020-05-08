package com.teletracker.common.elasticsearch.model

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsItemCrewMember(
  id: UUID,
  order: Option[Int],
  name: String,
  department: Option[String],
  job: Option[String],
  slug: Option[Slug])
