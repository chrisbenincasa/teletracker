package com.chrisbenincasa.services.teletracker.db

import com.chrisbenincasa.services.teletracker.auth.PasswordHash
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.DbProvider
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class UsersDbAccess @Inject()(
  val provider: DbProvider,
  val users: Users,
  val userCredentials: UserCredentials,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events
)(implicit executionContext: ExecutionContext) extends DbAccess {
  private val Pbkdf2Algorithm = "PBKDF2WithHmacSHA1"
  private val SALT_BYTES = 24
  private val HASH_BYTES = 24
  private val PBKDF2_ITERATIONS = 1000

  private val ITERATION_INDEX = 0
  private val SALT_INDEX = 1
  private val PBKDF2_INDEX = 2

  import provider.driver.api._

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
        _ <- (trackedLists.query returning trackedLists.query.map(_.id)) += TrackedListRow(None, "Default List", isDefault = true, userId)
      } yield userId
    }
  }

  def findUserAndLists(userId: Int) = {
    trackedLists.query.filter(_.userId === userId).flatMap(list => {
      list.userId_fk.map(user => user -> list)
    })
  }

  def findUserAndList(userId: Int, listId: Int) = {
    trackedLists.query.filter(tl => tl.userId === userId && tl.id === listId).take(1).flatMap(list => {
      list.userId_fk.map(user => user -> list)
    })
  }

  def findDefaultListForUser(userId: Int) = {
    trackedLists.query.filter(tl => tl.userId === userId && tl.isDefault === true).take(1)
  }

  def findList(userId: Int, listId: Int) = {
    val listQuery = trackedLists.query.filter(tl => tl.userId === userId && tl.id === listId)

    val fullQuery = listQuery joinLeft
      trackedListThings.query on (_.id === _.listId) joinLeft
      things.query on(_._2.map(_.thingId) === _.id)

    fullQuery.map {
      case ((list, _), things) => list -> things
    }
  }

  def addThingToList(listId: Int, thingId: Int) = {
    trackedListThings.query.insertOrUpdate(TrackedListThing(listId, thingId))
  }

  def getUserEvents(userId: Int) = {
    events.query.filter(_.userId === userId).sortBy(_.timestamp.desc)
  }

  def addUserEvent(event: Event) = {
    (events.query returning events.query.map(_.id)) += event
  }
}

case class SlickDBNoAvailableThreadsException(message: String) extends Exception(message)

