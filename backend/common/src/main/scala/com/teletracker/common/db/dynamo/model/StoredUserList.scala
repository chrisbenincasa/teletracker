package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.{DynamicListRules, TrackedListRowOptions}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import io.circe.parser.decode
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.OffsetDateTime
import java.util.UUID
import io.circe.syntax._
import java.util
import scala.collection.JavaConverters._

@JsonCodec
case class UserListRowOptions(removeWatchedItems: Boolean)

object StoredUserList {
  def primaryKey(
    id: UUID,
    userId: String
  ): util.Map[String, AttributeValue] =
    Map(
      "id" -> id.toString.toAttributeValue,
      "userId" -> userId.toAttributeValue
    ).asJava

  def fromRow(row: java.util.Map[String, AttributeValue]): StoredUserList = {
    StoredUserList(
      id = row.get("id").valueAs[UUID],
      name = row.get("name").valueAs[String],
      isDefault = row.get("isDefault").valueAs[Boolean],
      isPublic = row.get("isPublic").valueAs[Boolean],
      userId = row.get("userId").valueAs[String],
      isDynamic = row.get("isDynamic").valueAs[Boolean],
      rules = Option(row.get("rules"))
        .map(_.valueAs[String])
        .map(decode[DynamicListRules](_).right.get),
      options = Option(row.get("options"))
        .map(_.valueAs[String])
        .map(decode[UserListRowOptions](_).right.get),
      deletedAt = Option(row.get("deletedAt"))
        .map(_.valueAs[String])
        .map(OffsetDateTime.parse(_)),
      createdAt = Option(row.get("createdAt"))
        .map(_.valueAs[String])
        .map(OffsetDateTime.parse(_)),
      lastUpdatedAt = Option(row.get("lastUpdatedAt"))
        .map(_.valueAs[String])
        .map(OffsetDateTime.parse(_)),
      legacyId = Option(row.get("legacyId")).map(_.valueAs[Int])
    )
  }
}

case class StoredUserList(
  id: UUID,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  isDynamic: Boolean = false,
  rules: Option[DynamicListRules] = None,
  options: Option[UserListRowOptions] = None,
  createdAt: Option[OffsetDateTime] = None,
  deletedAt: Option[OffsetDateTime] = None,
  lastUpdatedAt: Option[OffsetDateTime] = None,
  legacyId: Option[Int] = None) {

  def primaryKey: util.Map[String, AttributeValue] =
    StoredUserList.primaryKey(id, userId)

  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    (Map(
      "id" -> id.toString.toAttributeValue,
      "name" -> name.toAttributeValue,
      "userId" -> userId.toAttributeValue,
      "isDefault" -> isDefault.toAttributeValue,
      "isPublic" -> isPublic.toAttributeValue,
      "isDynamic" -> isDynamic.toAttributeValue
    ) ++ Map(
      "rules" -> rules.map(_.asJson.noSpaces.toAttributeValue),
      "options" -> options.map(_.asJson.noSpaces.toAttributeValue),
      "deletedAt" -> deletedAt.map(_.toString.toAttributeValue),
      "createdAt" -> createdAt.map(_.toString.toAttributeValue),
      "lastUpdatedAt" -> lastUpdatedAt.map(_.toString.toAttributeValue),
      "legacyId" -> legacyId.map(_.toAttributeValue)
    ).collect {
      case (k, Some(v)) => k -> v
    }).asJava
  }
}
