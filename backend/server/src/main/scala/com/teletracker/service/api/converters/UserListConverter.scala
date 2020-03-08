package com.teletracker.service.api.converters

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.service.api.model.{UserList, UserListConfiguration}
import com.teletracker.service.auth.CurrentAuthenticatedUser
import javax.inject.{Inject, Provider, Singleton}

@Singleton
class UserListConverter @Inject()(
  currentAuthenticatedUser: Provider[Option[CurrentAuthenticatedUser]]) {
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
      createdAt = storedUserList.createdAt,
      aliases = storedUserList.aliases,
      ownedByRequester = currentAuthenticatedUser
        .get()
        .map(_.userId)
        .contains(storedUserList.userId)
    )
  }
}
