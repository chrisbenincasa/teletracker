package com.teletracker.common.pubsub

import io.circe.Json
import io.circe.generic.JsonCodec

object JobTags {
  final val RequiresTmdbApi = "tag/RequiresTmdbApi"
  final val RequiresDbAccess = "tag/RequiresDbAccess"
}

class EventBase extends Serializable {
  var receipt_handle: Option[String] = None
  var queued_timestamp: Option[Long] = None

  /**
    * Defines a unique identifier for this message for use in de-duplicating messages.
    * Note that there is no default: it must be defined for you to use it.
    * Note: This should not exceed 2048 bytes when using a dynamodb lock manager. (http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Limits.html)
    */
  def getUniqueMessageId(): Option[String] = None
}

@JsonCodec
case class TeletrackerTaskQueueMessage(
  clazz: String,
  args: Map[String, Json],
  jobTags: Option[Set[String]] = Some(Set.empty))
    extends EventBase

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
