package com.teletracker.common.elasticsearch.model

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsItemRecommendation(
  id: UUID,
  title: String,
  slug: Option[Slug])
    extends EsDenormalizedItemLike
