package com.teletracker.common.pubsub

import io.circe.Json
import io.circe.generic.JsonCodec
import java.util.UUID

object TaskTag {
  final val RequiresTmdbApi = "tag/RequiresTmdbApi"
  final val RequiresDbAccess = "tag/RequiresDbAccess"
}

class EventBase extends Serializable with SettableReceiptHandle {
  var receipt_handle: Option[String] = None
  var queued_timestamp: Option[Long] = None

  override def receiptHandle: Option[String] =
    receipt_handle

  override def setReceiptHandle(handle: Option[String]): Unit =
    receipt_handle = handle

  /**
    * Defines a unique identifier for this message for use in de-duplicating messages.
    * Note that there is no default: it must be defined for you to use it.
    * Note: This should not exceed 2048 bytes when using a dynamodb lock manager. (http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Limits.html)
    */
  def getUniqueMessageId(): Option[String] = None
}

@JsonCodec
case class TeletrackerTaskQueueMessage(
  id: Option[UUID],
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
      Some(UUID.randomUUID()),
      clazz,
      args.as[Option[Map[String, Json]]].right.get.getOrElse(Map.empty),
      tags
    )
  }
}
