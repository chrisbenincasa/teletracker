package com.teletracker.common.pubsub

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.generic.JsonCodec

@JsonCodec
case class EsIngestMessage(
  operation: EsIngestMessageOperation,
  update: Option[EsIngestUpdate] = None,
  index: Option[EsIngestIndex] = None)
    extends EventBase

@JsonCodec
case class EsIngestIndex(
  index: String,
  id: String,
  externalIdMappings: Option[Set[EsIngestItemExternalIdMapping]],
  doc: Json)

@JsonCodec
case class EsIngestItemExternalIdMapping(
  source: ExternalSource,
  id: String,
  itemType: ItemType)

@JsonCodec
case class EsIngestUpdate(
  index: String,
  id: String,
  itemType: ItemType,
  script: Option[Json],
  doc: Option[Json],
  itemDenorm: Option[EsIngestItemDenormArgs] = None,
  personDenorm: Option[EsIngestPersonDenormArgs] = None)

@JsonCodec
case class EsIngestItemDenormArgs(
  needsDenorm: Boolean,
  cast: Boolean,
  crew: Boolean,
  externalIds: Option[Boolean] = None)

object EsIngestItemDenormArgs {
  final val noDenorm: EsIngestItemDenormArgs = EsIngestItemDenormArgs(
    needsDenorm = false,
    cast = false,
    crew = false,
    externalIds = None
  )
}

@JsonCodec
case class EsIngestPersonDenormArgs(needsDenorm: Boolean)
