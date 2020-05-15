package com.teletracker.common.pubsub

import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsDenormalizeItemMessage(
  itemId: UUID,
  creditsChanged: Boolean,
  crewChanged: Boolean,
  dryRun: Boolean)
    extends EventBase
