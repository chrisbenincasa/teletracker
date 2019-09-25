package com.teletracker.common.pubsub

import io.circe.Json
import io.circe.generic.JsonCodec

object JobTags {
  final val RequiresTmdbApi = "tag/RequiresTmdbApi"
  final val RequiresDbAccess = "tag/RequiresDbAccess"
}

@JsonCodec
case class TeletrackerTaskQueueMessage(
  clazz: String,
  args: Map[String, Json],
  jobTags: Set[String] = Set.empty)
