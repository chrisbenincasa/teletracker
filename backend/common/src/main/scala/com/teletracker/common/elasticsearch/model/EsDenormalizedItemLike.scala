package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec
import java.util.UUID

trait EsDenormalizedItemLike {
  def id: UUID
  def title: String
}

@JsonCodec
case class EsMinimalDenormalizedItem(
  id: UUID,
  title: String)
    extends EsDenormalizedItemLike
