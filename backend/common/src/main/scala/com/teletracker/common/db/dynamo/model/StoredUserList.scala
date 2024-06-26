package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.DynamicListRules
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.OffsetDateTime
import java.util
import java.util.UUID
import scala.collection.JavaConverters._

@JsonCodec
case class UserListRowOptions(removeWatchedItems: Boolean)

object StoredUserList {
  final val PublicUserId = "public-user"

  def primaryKey(id: UUID): util.Map[String, AttributeValue] =
    Map(
      "id" -> id.toString.toAttributeValue
    ).asJava

  def fromRow(row: java.util.Map[String, AttributeValue]): StoredUserList = {
    StoredUserList(
      id = row.get("id").fromAttributeValue[UUID],
      name = row.get("name").fromAttributeValue[String],
      isDefault = row.get("isDefault").fromAttributeValue[Boolean],
      isPublic = row.get("isPublic").fromAttributeValue[Boolean],
      userId = row.get("userId").fromAttributeValue[String],
      isDynamic = row.get("isDynamic").fromAttributeValue[Boolean],
      rules = Option(row.get("rules"))
        .map(_.fromAttributeValue[String])
        .map(decode[DynamicListRules](_).right.get),
      options = Option(row.get("options"))
        .map(_.fromAttributeValue[String])
        .map(decode[UserListRowOptions](_).right.get),
      deletedAt = Option(row.get("deletedAt"))
        .map(_.fromAttributeValue[String])
        .map(OffsetDateTime.parse(_)),
      createdAt = Option(row.get("createdAt"))
        .map(_.fromAttributeValue[String])
        .map(OffsetDateTime.parse(_)),
      lastUpdatedAt = Option(row.get("lastUpdatedAt"))
        .map(_.fromAttributeValue[String])
        .map(OffsetDateTime.parse(_)),
      legacyId = Option(row.get("legacyId")).map(_.fromAttributeValue[Int]),
      aliases =
        Option(row.get("aliases")).map(_.fromAttributeValue[Set[String]])
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
  legacyId: Option[Int] = None,
  aliases: Option[Set[String]] = None) {

  def primaryKey: util.Map[String, AttributeValue] =
    StoredUserList.primaryKey(id)

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
      "legacyId" -> legacyId.map(_.toAttributeValue),
      "aliases" -> aliases.filter(_.nonEmpty).map(_.toAttributeValue)
    ).collect {
      case (k, Some(v)) => k -> v
    }).asJava
  }
}
