package com.teletracker.service.api

import com.teletracker.service.auth.PasswordHash
import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.common.db.access.{ListsDbAccess, UsersDbAccess}
import com.teletracker.common.db.model.{
  TokenRow,
  TrackedListFactory,
  User,
  UserRow
}
import javax.inject.Inject
import java.time.{Instant, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

class UsersApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess,
  jwtVendor: JwtVendor
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

  def newUser(
    name: String,
    username: String,
    email: String,
    password: String
  )(implicit executionContext: ExecutionContext
  ): Future[Int] = {
    val timestamp = Instant.now()
    val hashed = PasswordHash.createHash(password)
    val user = UserRow(
      None,
      name,
      username,
      email,
      hashed,
      timestamp,
      timestamp,
      preferences = None
    )

    for {
      userId <- usersDbAccess.insertUser(user)
      _ <- listsDbAccess.insertLists(
        List(
          TrackedListFactory.defaultList(userId),
          TrackedListFactory.watchedList(userId)
        )
      )
    } yield {
      userId
    }
  }

  def createUserAndToken(
    name: String,
    username: String,
    email: String,
    password: String
  ): Future[(Int, String)] = {
    for {
      userId <- newUser(name, username, email, password)
      token <- vendToken(email)
    } yield {
      (userId, token)
    }
  }

  def vendToken(email: String): Future[String] = {
    usersDbAccess.findByEmail(email).flatMap {
      case None => throw new IllegalArgumentException
      case Some(user) =>
        usersDbAccess
          .revokeAllTokens(user.id.get)
          .flatMap(_ => {
            val token = jwtVendor.vend(email)
            val now = OffsetDateTime.now()
            usersDbAccess
              .insertToken(
                TokenRow(
                  None,
                  user.id.get,
                  token,
                  now,
                  now,
                  None
                )
              )
              .map(_ => token)
          })
    }
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
