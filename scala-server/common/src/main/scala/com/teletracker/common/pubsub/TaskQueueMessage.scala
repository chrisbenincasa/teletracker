package com.teletracker.common.pubsub

import io.circe.Json
import io.circe.generic.JsonCodec

@JsonCodec
case class TeletrackerTaskQueueMessage(
  clazz: String,
  args: Map[String, Json])
