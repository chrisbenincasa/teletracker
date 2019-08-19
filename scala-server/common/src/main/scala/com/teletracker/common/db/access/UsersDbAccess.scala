package com.teletracker.common.db.access

import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.common.db.model.{
  Events,
  Things,
  TrackedListThings,
  TrackedLists,
  _
}
import com.teletracker.common.inject.{DbImplicits, DbProvider}
import com.teletracker.common.util.{Field, ListFilters, NetworkCache}
import javax.inject.{Inject, Provider}
import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UsersDbAccess @Inject()(
  val provider: DbProvider,
  val userMetadata: UsersMetadata,
  val userNetworkPreferences: UserNetworkPreferences,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events,
  val networks: Networks,
  val userThingTags: UserThingTags,
  listQuery: Provider[ListQuery],
  dynamicListBuilder: DynamicListBuilder,
  jwtVendor: JwtVendor,
  dbImplicits: DbImplicits,
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends DbAccess {
  import dbImplicits._
  import provider.driver.api._

  def updateUserMetadata(
    userId: String,
    preferences: UserPreferences
  ): Future[Unit] = {
    run {
      userMetadata.query
        .filter(_.userId === userId)
        .map(u => (u.lastUpdatedAt, u.preferences))
        .update((Instant.now(), Some(preferences)))
        .map(_ => {})
    }
  }

  def findMetadataForUser(userId: String): Future[UserMetadataRow] = {
    run {
      userMetadata.query.filter(_.userId === userId).result.headOption.flatMap {
        case Some(meta) => DBIO.successful(meta)
        case None =>
          val meta = UserMetadataRow(userId, None, Instant.now(), Instant.now())
          (userMetadata.query += meta).map(_ => meta)
      }
    }
  }

  def findNetworkPreferencesForUser(userId: String): Future[Seq[Network]] = {
    run {
      findNetworkPreferencesForUserQuery(userId)
    }
  }

  def findNetworkPreferencesForUpdate(
    userId: String
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
    userId: String,
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
    userId: String
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
    userId: String,
    includeThings: Boolean
  ): Future[Seq[TrackedList]] = {
    listQuery.get().findUsersLists(userId, includeThings = includeThings)
  }

  def findListForUser(
    userId: String,
    listId: Int
  ) = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.id === listId)
        .take(1)
        .result
        .headOption // TODO: dont select all metadata
    }
  }

  def findDefaultListForUser(userId: String): Future[Option[TrackedListRow]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.isDefault === true)
        .take(1)
        .result
        .headOption
    }
  }

  def insertList(
    userId: String,
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
    userId: String,
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

  def deleteList(
    userId: String,
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
    userId: String,
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
    userId: String,
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
    userId: String,
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
    thingId: UUID
  ): Future[Int] = {
    run {
      trackedListThings.query.insertOrUpdate(TrackedListThing(listId, thingId))
    }
  }

  def removeThingFromLists(
    listIds: Set[Int],
    thingId: UUID
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

  def getUserEvents(userId: String): Future[Seq[EventWithTarget]] = {
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
        case (event, Some(tid), tname @ Some(_)) =>
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
    userId: String,
    thingId: UUID,
    action: UserThingTagType,
    value: Option[Double]
  ): Future[Int] = {
    run {
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
    userId: String,
    thingId: UUID,
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
