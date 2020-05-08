package com.teletracker.common.elasticsearch.model

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsDenormalizedItem(
  id: UUID,
  title: String,
  `type`: String,
  slug: Slug)
    extends EsDenormalizedItemLike
