package com.teletracker.common.pubsub

import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsDenormalizePersonMessage(personId: UUID) extends EventBase
