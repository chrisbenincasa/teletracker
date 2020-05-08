package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsUserItem(
  id: String,
  item_id: Option[UUID],
  user_id: Option[String],
  tags: List[EsUserItemTag],
  item: Option[EsUserDenormalizedItem])
