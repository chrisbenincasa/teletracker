package com.teletracker.service.api

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.{ListsDbAccess, UsersDbAccess}
import com.teletracker.common.db.model.{TrackedListFactory, UserPreferences}
import com.teletracker.common.util.{FactoryImplicits, GeneralizedDbFactory}
import com.teletracker.service.api.model.UserDetails
import com.teletracker.service.controllers.UpdateUserRequestPayload
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UsersApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext) {

  def getUser(userId: String): Future[UserDetails] = {
    val metadataFut = usersDbAccess.findMetadataForUser(userId)
    val networkPrefsFut = usersDbAccess.findNetworkPreferencesForUser(userId)

    for {
      meta <- metadataFut
      nets <- networkPrefsFut
    } yield {
      UserDetails(
        userId,
        meta.preferences.getOrElse(UserPreferences.default),
        nets.toList
      )
    }
  }

  def updateUser(
    userId: String,
    updateUserRequest: UpdateUserRequestPayload
  ): Future[Unit] = {
    updateUserRequest.networkSubscriptions
      .map(networkSubscriptions => {
        usersDbAccess
          .findNetworkPreferencesForUpdate(userId)
          .flatMap(prefs => {
            val networkIds = networkSubscriptions.flatMap(_.id).toSet
            val existingNetworkIds = prefs.map(_.networkId).toSet

            val toDelete =
              prefs.filter(pref => !networkIds(pref.networkId)).map(_.id).toSet
            val toAdd = networkSubscriptions
              .filter(
                net => net.id.isDefined && !existingNetworkIds(net.id.get)
              )
              .flatMap(_.id)
              .toSet

            usersDbAccess
              .updateUserNetworkPreferences(
                userId,
                toAdd,
                toDelete
              )
          })
      })
      .getOrElse(Future.unit)
      .flatMap(_ => {
        updateUserRequest.userPreferences
          .map(usersDbAccess.updateUserMetadata(userId, _))
          .getOrElse(Future.unit)
      })
  }

  def registerUser(userId: String) = {
    createDefaultListsForUser(userId)
  }

  def createDefaultListsForUser(userId: String): Future[Seq[Int]] = {
    listsDbAccess.insertLists(
      List(
        TrackedListFactory.defaultList(userId),
        TrackedListFactory.watchedList(userId)
      )
    )
  }
}
