package com.teletracker.service.api

import com.teletracker.service.db.UsersDbAccess
import com.teletracker.service.db.model.User
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UserApi @Inject()(
  usersDbAccess: UsersDbAccess
)(implicit executionContext: ExecutionContext) {
  def getUser(userId: Int): Future[Option[User]] = {
    val userFut = usersDbAccess.findById(userId)
    val prefsFut = usersDbAccess.findNetworkPreferencesForUser(userId)

    for {
      userOpt <- userFut
      networkPrefs <- prefsFut
    } yield {
      userOpt.map(user => {
        User
          .fromRow(user)
          .withNetworksSubscriptions(networkPrefs.toList)
      })
    }
  }

  def updateUser(updatedUser: User): Future[Unit] = {
    for {
      _ <- usersDbAccess.updateUser(
        updatedUser.id,
        updatedUser.name,
        updatedUser.username,
        updatedUser.userPreferences
      )
      _ <- updateUserPreferences(updatedUser)
    } yield {}
  }

  private def updateUserPreferences(updatedUser: User): Future[Unit] = {
    usersDbAccess
      .findNetworkPreferencesForUpdate(updatedUser.id)
      .flatMap(prefs => {
        val networkIds = updatedUser.networkSubscriptions.flatMap(_.id).toSet
        val existingNetworkIds = prefs.map(_.networkId).toSet

        val toDelete =
          prefs.filter(pref => !networkIds(pref.networkId)).map(_.id).toSet
        val toAdd = updatedUser.networkSubscriptions
          .filter(net => net.id.isDefined && !existingNetworkIds(net.id.get))
          .flatMap(_.id)
          .toSet

        usersDbAccess
          .updateUserNetworkPreferences(updatedUser.id, toAdd, toDelete)
      })
  }
}
