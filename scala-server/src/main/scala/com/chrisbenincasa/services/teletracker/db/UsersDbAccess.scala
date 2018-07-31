package com.chrisbenincasa.services.teletracker.db

import com.chrisbenincasa.services.teletracker.auth.PasswordHash
import com.chrisbenincasa.services.teletracker.auth.jwt.JwtVendor
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.{DbImplicits, DbProvider}
import javax.inject.Inject
import org.joda.time.DateTime
import scala.concurrent.{ExecutionContext, Future}

class UsersDbAccess @Inject()(
  val provider: DbProvider,
  val users: Users,
  val userCredentials: UserCredentials,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events,
  val tokens: Tokens,
  jwtVendor: JwtVendor,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext) extends DbAccess {
  import provider.driver.api._
  import dbImplicits._

  private implicit class UserExtensions[C[_]](q: Query[Users#UsersTable, UserRow, C]) {
    def withCredentials = q.join(userCredentials.query).on(_.id === _.userId)
  }

  def findById(id: Int) = {
    run {
      users.query.filter(_.id === id).result.headOption
    }
  }

  def findByEmail(email: String) = {
    run {
      users.query.filter(_.email === email).result.headOption
    }
  }

  def newUser(name: String, username: String, email: String, password: String)(implicit executionContext: ExecutionContext) = {
    val now = System.currentTimeMillis()
    val timestamp = new java.sql.Timestamp(now)
    val hashed = PasswordHash.createHash(password)
    val user = UserRow(None, name, username, email, hashed, timestamp, timestamp)

    val query = (users.query returning users.query.map(_.id)) += user

    run {
      for {
        userId <- query
        _ <- (trackedLists.query returning trackedLists.query.map(_.id)) += TrackedListRow(None, "Default List", isDefault = true, isPublic = false, userId)
      } yield userId
    }
  }

  def vendToken(email: String) = {
    findByEmail(email).flatMap {
      case None => throw new IllegalArgumentException
      case Some(user) =>
        revokeAllTokens(user.id.get).flatMap(_ => {
          val token = jwtVendor.vend(email)
          val now = DateTime.now()
          val insert = tokens.query += TokenRow(None, user.id.get, token, now, now, None)
          run(insert).map(_ => token)
        })
    }
  }

  def revokeAllTokens(userId: Int) = {
    run {
      tokens.query.
        filter(t => t.userId === userId && t.revokedAt.isEmpty).
        map(_.revokedAt).
        update(Some(DateTime.now()))
    }
  }

  def revokeToken(userId: Int, token: String) = {
    run {
      tokens.query.
        filter(t => t.userId === userId && t.token === token).
        map(_.revokedAt).
        update(Some(DateTime.now()))
    }
  }

  def getToken(token: String) = {
    run {
      tokens.query.filter(_.token === token).result.headOption
    }
  }

  def findUserAndListsQuery(userId: Int) = {
    users.query.filter(_.id === userId) joinLeft trackedLists.query on(_.id === _.userId) joinLeft
      trackedListThings.query on(_._2.map(_.id) === _.listId) joinLeft
      things.query on(_._2.map(_.thingId) === _.id)
  }

  def findListsForUser(userId: Int) = {
    run {
      trackedLists.query.filter(_.userId === userId).result
    }
  }

  def findUserAndLists(userId: Int) = {
    run {
      (for {
        (((user, list), _), thing) <- findUserAndListsQuery(userId)
      } yield {
        (user, list, thing.map(_.id), thing.map(_.name), thing.map(_.`type`))
      }).result
    }.map(_.map {
      case (user, optList, thingIdOpt, thingNameOpt, thingTypeOpt) =>
        (user, optList, thingIdOpt.map(id => PartialThing(Some(id), thingNameOpt, `type` = thingTypeOpt)))
    })
  }

  def findUserAndList(userId: Int, listId: Int) = {
    run {

      trackedLists.query.filter(tl => tl.userId === userId && tl.id === listId).take(1).flatMap(list => {
        list.userId_fk.map(user => user -> list)
      }).result
    }
  }

  def findDefaultListForUser(userId: Int) = {
    run {
      trackedLists.query.filter(tl => tl.userId === userId && tl.isDefault === true).
        take(1).
        result.
        headOption
    }
  }

  def insertList(userId: Int, name: String) = {
    run {
      (trackedLists.query  returning
        trackedLists.query.map(_.id) into
        ((l, id) => l.copy(id = Some(id)))) += TrackedListRow(None, name, isDefault = false, isPublic = false, userId)
    }
  }

  def findList(userId: Int, listId: Int) = {
    val listQuery = trackedLists.query.filter(tl => tl.userId === userId && tl.id === listId)

    val fullQuery = listQuery joinLeft
      trackedListThings.query on (_.id === _.listId) joinLeft
      things.query on(_._2.map(_.thingId) === _.id)

    val q = fullQuery.map {
      case ((list, _), things) => list -> things
    }

    run(q.result)
  }

  def addThingToList(listId: Int, thingId: Int) = {
    run {
      trackedListThings.query.insertOrUpdate(TrackedListThing(listId, thingId))
    }
  }

  def removeThingFromLists(listIds: Set[Int], thingId: Int) = {
    if (listIds.isEmpty) {
      Future.successful(0)
    } else {
      run {
        trackedListThings.query.
          filter(_.listId inSetBind listIds).
          filter(_.thingId === thingId).
          delete
      }
    }
  }

  def getUserEvents(userId: Int) = {
    run {
      (for {
        (ev, thing) <- events.query.filter(_.userId === userId).sortBy(_.timestamp.desc) joinLeft
          things.query on((ev, t) => ev.targetEntityId === t.id.asColumnOf[String])
      } yield {
        (ev, thing.map(_.id), thing.map(_.name))
      }).result.map(_.map {
        case (event, tid @ Some(_), tname @ Some(_)) =>
          event.withTarget(PartialThing(tid, tname))
        case (event, _, _) =>
          EventWithTarget(event, None)
      })
    }
  }

  def addUserEvent(event: Event) = {
    run {
      (events.query returning events.query.map(_.id)) += event
    }
  }
}

case class SlickDBNoAvailableThreadsException(message: String) extends Exception(message)

