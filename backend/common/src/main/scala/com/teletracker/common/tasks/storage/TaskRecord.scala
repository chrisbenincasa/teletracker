package com.teletracker.common.tasks.storage

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.util.HasId
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.generic.JsonCodec
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.net.URI
import java.time.Instant
import java.util.UUID
import scala.collection.JavaConverters._

@JsonCodec
case class TaskRecord(
  id: UUID,
  taskName: String,
  fullTaskName: Option[String],
  args: Json,
  status: TaskStatus,
  createdAt: Option[Instant],
  startedAt: Option[Instant],
  finishedAt: Option[Instant],
  teletrackerVersion: Option[String],
  gitSha: Option[String],
  logUri: Option[URI],
  hostname: Option[String]) {

  def toDynamoItem: java.util.Map[String, AttributeValue] =
    (Map(
      "id" -> id.toAttributeValue,
      "taskName" -> taskName.toAttributeValue,
      "status" -> status.toString.toAttributeValue,
      "args" -> args.noSpaces.toAttributeValue
    ) ++ Map(
      "fullTaskName" -> fullTaskName.map(_.toAttributeValue),
      "createdAt" -> createdAt.map(_.toAttributeValue),
      "startedAt" -> startedAt.map(_.toAttributeValue),
      "finishedAt" -> finishedAt.map(_.toAttributeValue),
      "teletrackerVersion" -> teletrackerVersion.map(_.toAttributeValue),
      "gitSha" -> gitSha.map(_.toAttributeValue),
      "logUri" -> logUri.map(_.toString).map(_.toAttributeValue)
    ).collect {
      case (k, Some(v)) => k -> v
    }).asJava
}

object TaskRecord {
  implicit final val hasId: HasId.Aux[TaskRecord, UUID] =
    HasId.instance[TaskRecord, UUID](_.id)
}
