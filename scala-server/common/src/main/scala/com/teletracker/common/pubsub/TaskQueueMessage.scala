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
  jobTags: Option[Set[String]] = Some(Set.empty))

object TeletrackerTaskQueueMessageFactory {

  def withJsonArgs(
    clazz: String,
    args: Json,
    tags: Option[Set[String]]
  ): TeletrackerTaskQueueMessage = {
    TeletrackerTaskQueueMessage(
      clazz,
      args.as[Map[String, Json]].right.get,
      tags
    )
  }
}
