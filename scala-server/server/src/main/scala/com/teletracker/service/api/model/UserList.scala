package com.teletracker.service.api.model

import com.teletracker.common.db.model.TrackedListRow
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import java.time.OffsetDateTime

object UserList {
  implicit val codec: Codec[UserList] = io.circe.generic.semiauto.deriveCodec

  def fromRow(row: TrackedListRow): UserList = {
    UserList(
      row.id,
      row.name,
      row.isDefault,
      row.isPublic,
      row.userId,
      items = None,
      isDynamic = row.isDynamic,
      configuration = UserListConfiguration.fromRow(row.rules, row.options),
      deletedAt = row.deletedAt
    )
  }
}

case class UserList(
  id: Int,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  configuration: UserListConfiguration,
  items: Option[List[Item]] = None,
  isDynamic: Boolean = false,
  isDeleted: Boolean = false,
  deletedAt: Option[OffsetDateTime] = None,
  totalItems: Option[Int] = None,
  relevantPeople: Option[List[Person]] = None) {
  def withItems(items: List[Item]): UserList = {
    this.copy(items = Some(items))
  }

  def withPeople(people: List[Person]): UserList = {
    this.copy(relevantPeople = Some(people))
  }

  def withCount(count: Int): UserList = this.copy(totalItems = Some(count))
}
