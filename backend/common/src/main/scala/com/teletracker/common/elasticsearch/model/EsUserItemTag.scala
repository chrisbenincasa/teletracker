package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.UserThingTagType
import io.circe.Codec
import io.circe.generic.JsonCodec
import java.time.Instant
import java.util.UUID

object EsUserItemTag {
  implicit val codec: Codec[EsUserItemTag] =
    io.circe.generic.semiauto.deriveCodec

  def noValue(tag: UserThingTagType): EsUserItemTag = {
    EsUserItemTag(tag = tag.toString, last_updated = Some(Instant.now()))
  }

  def value[T](
    userId: String,
    itemId: UUID,
    tag: UserThingTagType,
    value: Option[T]
  )(implicit esItemTaggable: EsItemTaggable[T]
  ): EsUserItemTag = esItemTaggable.makeUserItemTag(userId, itemId, tag, value)

  def forInt(
    tag: UserThingTagType,
    value: Int
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    int_value = Some(value),
    last_updated = Some(Instant.now())
  )

  def forDouble(
    tag: UserThingTagType,
    value: Double
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    double_value = Some(value),
    last_updated = Some(Instant.now())
  )

  def forString(
    tag: UserThingTagType,
    value: String
  ): EsUserItemTag = EsUserItemTag(
    tag = tag.toString,
    string_value = Some(value),
    last_updated = Some(Instant.now())
  )

  def belongsToList(listId: UUID): EsUserItemTag = {
    forString(UserThingTagType.TrackedInList, listId.toString)
  }
}

@JsonCodec
case class EsUserItemTag(
  tag: String,
  user_id: Option[String] = None,
  item_id: Option[String] = None,
  int_value: Option[Int] = None,
  double_value: Option[Double] = None,
  date_value: Option[Instant] = None,
  string_value: Option[String] = None,
  last_updated: Option[Instant])
