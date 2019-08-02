package com.teletracker.service.db.access

import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.controllers.ListFilters
import com.teletracker.service.db.model.{
  Events,
  Things,
  Tokens,
  TrackedListThings,
  TrackedLists,
  UserCredentials,
  Users,
  _
}
import com.teletracker.service.inject.{DbImplicits, DbProvider}
import com.teletracker.service.util.Field
import javax.inject.{Inject, Provider}
import java.time.{Instant, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}

class UsersDbAccess @Inject()(
  val provider: DbProvider,
  val users: Users,
  val userNetworkPreferences: UserNetworkPreferences,
  val userCredentials: UserCredentials,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events,
  val tokens: Tokens,
  val networks: Networks,
  val userThingTags: UserThingTags,
  listQuery: Provider[ListQuery],
  dynamicListBuilder: DynamicListBuilder,
  jwtVendor: JwtVendor,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext)
    extends DbAccess {
  import dbImplicits._
  import provider.driver.api._

  implicit private class UserExtensions[C[_]](
    q: Query[Users#UsersTable, UserRow, C]) {
    def withCredentials = q.join(userCredentials.query).on(_.id === _.userId)
  }

  def findById(id: Int): Future[Option[UserRow]] = {
    run {
      users.query.filter(_.id === id).result.headOption
    }
  }

  def findByEmail(email: String): Future[Option[UserRow]] = {
    run {
      users.query.filter(_.email === email).result.headOption
    }
  }

  def insertUser(user: UserRow): Future[Int] = {
    run {
      (users.query returning users.query.map(_.id)) += user
    }
  }

  def updateUser(
    id: Int,
    name: String,
    username: String,
    preferences: UserPreferences
  ): Future[Unit] = {
    run {
      users.query
        .filter(_.id === id)
        .map(u => {
          (u.name, u.username, u.lastUpdatedAt, u.preferences)
        })
        .update(
          (
            name,
            username,
            Instant.now(),
            Some(preferences)
          )
        )
        .map(_ => {})
    }
  }

  def insertToken(token: TokenRow): Future[Int] = {
    run {
      tokens.query += token
    }
  }

  def revokeAllTokens(userId: Int): Future[Int] = {
    run {
      tokens.query
        .filter(t => t.userId === userId && t.revokedAt.isEmpty)
        .map(_.revokedAt)
        .update(Some(OffsetDateTime.now()))
    }
  }

  def revokeToken(
    userId: Int,
    token: String
  ): Future[Int] = {
    run {
      tokens.query
        .filter(t => t.userId === userId && t.token === token)
        .map(_.revokedAt)
        .update(Some(OffsetDateTime.now()))
    }
  }

  def getToken(token: String): Future[Option[TokenRow]] = {
    run {
      tokens.query.filter(_.token === token).result.headOption
    }
  }

  def findNetworkPreferencesForUser(id: Int): Future[Seq[Network]] = {
    run {
      findNetworkPreferencesForUserQuery(id)
    }
  }

  def findNetworkPreferencesForUpdate(
    userId: Int
  ): Future[Seq[UserNetworkPreference]] = {
    run {
      userNetworkPreferences.query
        .filter(
          _.userId === userId
        )
        .result
    }
  }

  def updateUserNetworkPreferences(
    userId: Int,
    networksToAdd: Set[Int],
    networksToDelete: Set[Int]
  ): Future[Unit] = {
    val deleteAction = if (networksToDelete.nonEmpty) {
      userNetworkPreferences.query
        .filter(_.id inSetBind networksToDelete)
        .delete
    } else {
      DBIO.successful(0)
    }

    val addAction = if (networksToAdd.nonEmpty) {
      val networkPrefs = networksToAdd.map(network => {
        UserNetworkPreference(-1, userId, network)
      })
      userNetworkPreferences.query ++= networkPrefs
    } else {
      DBIO.successful(None)
    }

    run {
      DBIO.seq(
        deleteAction,
        addAction
      )
    }
  }

  private def findNetworkPreferencesForUserQuery(
    userId: Int
  ): DBIOAction[Seq[Network], NoStream, Effect.Read] = {
    for {
      prefsAndNetworks <- (userNetworkPreferences.query.filter(
        _.userId === userId
      ) joinLeft
        networks.query on (_.networkId === _.id)).result
    } yield {
      prefsAndNetworks.flatMap(_._2)
    }
  }

  def findListsForUser(
    userId: Int,
    includeThings: Boolean
  ): Future[Seq[TrackedList]] = {
    listQuery.get().findUsersLists(userId, includeThings = includeThings)
  }

  def findUserAndList(
    userId: Int,
    listId: Int
  ): Future[Seq[(UserRow, TrackedListRow)]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.id === listId)
        .take(1)
        .flatMap(list => {
          list.userId_fk.map(user => user -> list)
        })
        .result
    }
  }

  def findDefaultListForUser(userId: Int): Future[Option[TrackedListRow]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.isDefault === true)
        .take(1)
        .result
        .headOption
    }
  }

  def insertList(
    userId: Int,
    name: String
  ): Future[TrackedListRow] = {
    run {
      val newList = TrackedListRow(
        -1,
        name,
        isDefault = false,
        isPublic = false,
        userId
      )

      (trackedLists.query returning
        trackedLists.query.map(_.id) into
        ((l, id) => l.copy(id = id))) += newList
    }
  }

  def updateList(
    userId: Int,
    listId: Int,
    name: String
  ): Future[Int] = {
    run {
      trackedLists.query
        .filter(l => l.userId === userId && l.id === listId)
        .map(_.name)
        .update(name)
    }
  }

//  def getListById(userId: Int, listId: Int) = {
//    run {
//      trackedLists
//        .findSpecificListQuery(userId, listId)
//    }
//  }

  def deleteList(
    userId: Int,
    listId: Int,
    mergeWithList: Option[Int]
  ): Future[Boolean] = {
    val value = trackedLists
      .findSpecificListQuery(userId, listId)
    run {
      trackedLists
        .findSpecificListQuery(userId, listId)
        .map(_.map(_.deletedAt))
        .update(Some(OffsetDateTime.now()))
    }.flatMap {
      case 0 => Future.successful(false)

      case 1 if mergeWithList.isDefined =>
        mergeLists(userId, listId, mergeWithList.get).map(_ => true)

      case 1 => Future.successful(true)

      case _ => throw new IllegalStateException("")
    }
  }

  def mergeLists(
    userId: Int,
    sourceList: Int,
    targetList: Int
  ): Future[Unit] = {
    val sourceItemsQuery = trackedLists.query.filter(
      tl => tl.userId === userId && tl.id === sourceList
    ) joinLeft
      trackedListThings.query on (_.id === _.listId)

    val targetItemsQuery = trackedLists.query.filter(
      tl => tl.userId === userId && tl.id === targetList
    ) joinLeft
      trackedListThings.query on (_.id === _.listId)

    val sourceItemsFut = run(sourceItemsQuery.result).map(_.flatMap(_._2))
    val targetItemsFut = run(targetItemsQuery.result).map(_.flatMap(_._2))

    for {
      sourceItems <- sourceItemsFut
      targetItems <- targetItemsFut
      sourceIds = sourceItems.map(_.thingId)
      targetIds = targetItems.map(_.thingId)
      idsToInsert = sourceIds.toSet -- targetIds
      _ <- run {
        trackedListThings.query ++= idsToInsert.map(thingId => {
          TrackedListThing(targetList, thingId)
        })
      }
    } yield {}
  }

  private val defaultFields = List(Field("id"))

  implicit class Pipeliner[T](x: T) {
    def |>[U](f: T => U): U = {
      f(x)
    }
  }

  def getList(
    userId: Int,
    listId: Int
  ): Future[Option[TrackedListRow]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.id === listId)
        .take(1)
        .result
        .headOption
    }
  }

  def findList(
    userId: Int,
    listId: Int,
    includeMetadata: Boolean = true,
    selectFields: Option[List[Field]] = None,
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None
  ): Future[Option[TrackedList]] = {
    listQuery
      .get()
      .findList(
        userId,
        listId,
        includeMetadata,
        includeTags = true,
        selectFields,
        filters,
        isDynamicHint
      )
      .map(_.map {
        case (list, thingsAndActions) =>
          val things = thingsAndActions.map {
            case (thing, actions) =>
              thing.toPartial
                .withUserMetadata(UserThingDetails(Seq.empty, actions))
          }

          list.toFull.withThings(things.toList)
      })
  }

  def addThingToList(
    listId: Int,
    thingId: Int
  ): Future[Int] = {
    run {
      trackedListThings.query.insertOrUpdate(TrackedListThing(listId, thingId))
    }
  }

  def removeThingFromLists(
    listIds: Set[Int],
    thingId: Int
  ): Future[Int] = {
    if (listIds.isEmpty) {
      Future.successful(0)
    } else {
      run {
        trackedListThings.query
          .filter(_.listId inSetBind listIds)
          .filter(_.thingId === thingId)
          .delete
      }
    }
  }

  def getUserEvents(userId: Int): Future[Seq[EventWithTarget]] = {
    run {
      (for {
        (ev, thing) <- events.query
          .filter(_.userId === userId)
          .sortBy(_.timestamp.desc) joinLeft
          things.query on (
          (
            ev,
            t
          ) => ev.targetEntityId === t.id.asColumnOf[String]
        )
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

  def addUserEvent(event: Event): Future[Int] = {
    run {
      (events.query returning events.query.map(_.id)) += event
    }
  }

  def insertOrUpdateAction(
    userId: Int,
    thingId: Int,
    action: UserThingTagType,
    value: Option[Double]
  ): Future[Int] = {
    run {
//      sql"""
//        select
//      """
      userThingTags.query
        .filter(utt => {
          utt.userId === userId && utt.thingId === thingId && utt.action === action
        })
        .take(1)
        .result
        .headOption
        .flatMap {
          case Some(existing) =>
            userThingTags.query.update(existing.copy(value = value))

          case None =>
            userThingTags.query += UserThingTag(
              -1,
              userId,
              thingId,
              action,
              value
            )
        }
    }
  }

  def removeAction(
    userId: Int,
    thingId: Int,
    action: UserThingTagType
  ): Future[Int] = {
    run {
      userThingTags.query
        .filter(utt => {
          utt.userId === userId && utt.thingId === thingId && utt.action === action
        })
        .delete
    }
  }
}

case class SlickDBNoAvailableThreadsException(message: String)
    extends Exception(message)
