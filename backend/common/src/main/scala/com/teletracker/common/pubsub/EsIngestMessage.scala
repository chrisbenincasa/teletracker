package com.teletracker.common.pubsub

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.generic.JsonCodec

@JsonCodec
case class EsIngestMessage(
  operation: EsIngestMessageOperation,
  update: Option[EsIngestUpdate])
    extends EventBase

@JsonCodec
case class EsIngestUpdate(
  index: String,
  id: String,
  itemType: Option[ItemType],
  script: Option[Json],
  doc: Option[Json])
