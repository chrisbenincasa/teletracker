package com.teletracker.common.elasticsearch.model

import io.circe.Codec
import io.circe.generic.JsonCodec
import java.util.UUID

object EsUserItem {
  implicit val codec: Codec[EsUserItem] =
    io.circe.generic.semiauto.deriveCodec[EsUserItem]

  def makeId(
    userId: String,
    itemId: UUID
  ): String = s"${userId}_${itemId}"

  def create(
    itemId: UUID,
    userId: String,
    tags: List[EsUserItemTag],
    item: Option[EsUserDenormalizedItem]
  ): EsUserItem = {
    EsUserItem(
      id = makeId(userId, itemId),
      item_id = itemId,
      user_id = userId,
      tags = tags,
      item = item
    )
  }
}

@JsonCodec
case class EsUserItem(
  id: String,
  item_id: UUID,
  user_id: String,
  tags: List[EsUserItemTag],
  item: Option[EsUserDenormalizedItem])

@JsonCodec
case class EsUserItemFull(
  id: String,
  item_id: Option[UUID],
  user_id: Option[String],
  tags: List[EsUserItemTag],
  item: Option[EsItem])
