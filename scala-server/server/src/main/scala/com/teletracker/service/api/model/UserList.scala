package com.teletracker.service.api.model

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.model.TrackedListRow
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import java.time.OffsetDateTime
import java.util.UUID

object UserList {
  implicit val codec: Codec[UserList] = io.circe.generic.semiauto.deriveCodec

  def fromStoredList(storedUserList: StoredUserList): UserList = {
    UserList(
      storedUserList.id,
      storedUserList.legacyId,
      storedUserList.name,
      storedUserList.isDefault,
      storedUserList.isPublic,
      storedUserList.userId,
      items = None,
      isDynamic = storedUserList.isDynamic,
      configuration = UserListConfiguration
        .fromStoredConfiguration(storedUserList.rules, storedUserList.options),
      deletedAt = storedUserList.deletedAt,
      createdAt = storedUserList.createdAt
    )
  }
}

case class UserList(
  id: UUID,
  legacyId: Option[Int],
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  configuration: UserListConfiguration,
  items: Option[List[Item]] = None,
  isDynamic: Boolean = false,
  isDeleted: Boolean = false,
  createdAt: Option[OffsetDateTime] = None,
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
