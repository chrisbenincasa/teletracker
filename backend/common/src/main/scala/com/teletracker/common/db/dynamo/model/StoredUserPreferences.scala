package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.PresentationType
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import io.circe.parser.decode
import io.circe.syntax._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.collection.JavaConverters._

object StoredUserPreferences {
  final val Prefix = "USER_PREFERENCES_"

  def getKey(userId: String) =
    Map(
      "id" -> s"${StoredUserPreferences.Prefix}${userId}".toAttributeValue,
      MetadataFields.TypeField -> MetadataType.UserPreferencesType.toAttributeValue
    ).asJava

  def fromRow(
    row: java.util.Map[String, AttributeValue]
  ): StoredUserPreferences = {
    val rowMap = row.asScala

    require(
      rowMap("type")
        .fromAttributeValue[String] == MetadataType.GenreReferenceType
    )

    StoredUserPreferences(
      userId = rowMap("userId").fromAttributeValue[String],
      createdAt = Instant
        .ofEpochMilli(rowMap("createdAt").fromAttributeValue[Long])
        .atOffset(ZoneOffset.UTC),
      lastUpdatedAt = Instant
        .ofEpochMilli(rowMap("lastUpdatedAt").fromAttributeValue[Long])
        .atOffset(ZoneOffset.UTC),
      preferences = decode[StoredUserPreferencesBlob](
        rowMap("preferences").fromAttributeValue[String]
      ).right.get
    )
  }
}

case class StoredUserPreferences(
  userId: String,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  preferences: StoredUserPreferencesBlob) {

  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    Map(
      "id" -> s"${StoredUserPreferences.Prefix}${userId}".toAttributeValue,
      MetadataFields.TypeField -> MetadataType.UserPreferencesType.toAttributeValue,
      "userId" -> userId.toAttributeValue,
      "createdAt" -> createdAt.toInstant.toEpochMilli.toAttributeValue,
      "lastUpdatedAt" -> lastUpdatedAt.toInstant.toEpochMilli.toAttributeValue,
      "preferences" -> preferences.asJson.noSpaces.toAttributeValue
    ).asJava
  }
}

@JsonCodec
case class StoredUserPreferencesBlob(
  networkIds: Option[Set[Int]] = None,
  presentationTypes: Option[Set[PresentationType]] = None,
  showOnlyNetworkSubscriptions: Option[Boolean] = None,
  hideAdultContent: Option[Boolean] = None)
