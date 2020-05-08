package com.teletracker.common.elasticsearch.model

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsItemCastMember(
  character: Option[String],
  id: UUID,
  order: Int,
  name: String,
  slug: Option[Slug])
