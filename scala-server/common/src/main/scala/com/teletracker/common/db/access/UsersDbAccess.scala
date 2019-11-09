package com.teletracker.common.db.access

import com.teletracker.common.api.model.{
  TrackedList,
  TrackedListOptions,
  TrackedListRules
}
import com.teletracker.common.db.model.{
  Events,
  Things,
  TrackedListThings,
  TrackedLists,
  _
}
import com.teletracker.common.db.util.InhibitFilter
import com.teletracker.common.db.{
  BaseDbProvider,
  Bookmark,
  DbImplicits,
  DbMonitoring,
  DefaultForListType,
  SortMode
}
import com.teletracker.common.util.{
  FactoryImplicits,
  Field,
  ListFilters,
  NetworkCache,
  Slug
}
import javax.inject.{Inject, Provider}
import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import com.teletracker.common.util.Functions._
import slick.basic.DatabasePublisher
import scala.concurrent.{ExecutionContext, Future}

class UsersDbAccess @Inject()(
  val provider: BaseDbProvider,
  val userMetadata: UsersMetadata,
  val userNetworkPreferences: UserNetworkPreferences,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events,
  val networks: Networks,
  val userThingTags: UserThingTags,
  listQuery: ListQuery,
  dynamicListBuilder: DynamicListBuilder,
  dbImplicits: DbImplicits,
  networkCache: NetworkCache,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring)
    with FactoryImplicits {
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

  def findListsForUserRaw(userId: String) = {
    run {
      trackedLists.query
        .filter(_.userId === userId)
        .filter(_.deletedAt.isEmpty)
        .result
    }
  }

  def findListsForUser(
    userId: String,
    includeThings: Boolean
  ): Future[Seq[TrackedList]] = {
    listQuery.findUsersLists(userId, includeThings = includeThings)
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
    name: String,
    rules: Option[DynamicListRules]
  ): Future[TrackedListRow] = {
    run {
      val newList = TrackedListRow(
        -1,
        name,
        isDefault = false,
        isPublic = false,
        userId,
        isDynamic = rules.isDefined,
        rules = rules
      )

      (trackedLists.query returning
        trackedLists.query.map(_.id) into
        ((l, id) => l.copy(id = id))) += newList
    }
  }

  def updateList(
    userId: String,
    listId: Int,
    name: Option[String],
    rules: Option[TrackedListRules],
    options: Option[TrackedListOptions]
  ): Future[Option[ListUpdateResult]] = {
    val rulesDao = rules.map(_.toRow)
    val optionsDao = options.map(_.toRow)

    // TODO: Move this to API
    run {
      trackedLists
        .findSpecificListQuery(userId, listId)
        .result
        .headOption
        .flatMap {
          case None => DBIO.successful(None)
          case Some(list) =>
            val updatedList = list
              .copy(
                name = name.getOrElse(list.name),
                rules = rulesDao.orElse(list.rules),
                options = optionsDao.orElse(list.options)
              )
              .applyIf(options.exists(_.removeWatchedItems) && list.isDynamic)(
                addRemoveWhenWatchedRule
              )
              .applyIf(options.exists(!_.removeWatchedItems) && list.isDynamic)(
                removeRemoveWhenWatchedRule
              )

            trackedLists
              .findSpecificListQuery(userId, listId)
              .update(updatedList)
              .map {
                case 1 =>
                  Some(
                    ListUpdateResult(
                      list.rules,
                      rulesDao.orElse(list.rules),
                      list.options,
                      optionsDao.orElse(list.options)
                    )
                  )
                case _ => None
              }
        }
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
          TrackedListThing(targetList, thingId, OffsetDateTime.now(), None)
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

  def getListById(listId: Int) = {
    run {
      trackedLists.query
        .filter(tl => tl.id === listId)
        .take(1)
        .result
        .headOption
    }
  }

  def getAllLists() = {
    run {
      trackedLists.query.result
    }
  }

  def findList(
    userId: String,
    listId: Int,
    includeMetadata: Boolean = true,
    selectFields: Option[List[Field]] = None,
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None,
    sortMode: SortMode = DefaultForListType(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 10
  ): Future[ListQueryResult] = {
    listQuery
      .findList(
        userId,
        listId,
        includeMetadata,
        includeTags = true,
        selectFields,
        filters,
        isDynamicHint,
        sortMode,
        bookmark,
        limit
      )
  }

  def addThingToList(
    listId: Int,
    thingId: UUID
  ): Future[Int] = {
    run {
      // TODO is this wrong - addedTime
      trackedListThings.query.insertOrUpdate(
        TrackedListThing(listId, thingId, OffsetDateTime.now(), None)
      )
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
          .map(_.removedAt)
          .update(Some(OffsetDateTime.now()))
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

  def withThingId(idOrSlug: Either[UUID, Slug]) = {
    idOrSlug match {
      case Left(id) => DBIO.successful(Some(id))
      case Right(slug) =>
        things.query
          .filter(_.normalizedName === slug)
          .take(1)
          .map(_.id)
          .result
          .headOption
    }
  }

  def loopThroughAllListTracking(
    offset: Int = 0,
    perPage: Int = 50,
    limit: Int = -1,
    startingId: Option[Int] = None
  )(
    process: Seq[TrackedListThing] => Future[Unit]
  ): Future[Unit] = {
    provider.getDB.stream(userThingTags.query.result)

    if (limit > 0 && offset >= limit) {
      Future.unit
    } else {
      run {
        InhibitFilter(trackedListThings.query)
          .filter(startingId)(id => _.listId >= id)
          .query
          .sortBy(_.listId.asc.nullsFirst)
          .drop(offset)
          .take(perPage)
          .result
      }.flatMap {
        case x if x.isEmpty => Future.unit
        case x =>
          process(x).flatMap(
            _ =>
              loopThroughAllListTracking(offset + perPage, perPage, limit)(
                process
              )
          )
      }
    }
  }

  def loopThroughAllUserActions(
    offset: Int = 0,
    perPage: Int = 50,
    limit: Int = -1,
    startingId: Option[String] = None
  )(
    process: Seq[UserThingTag] => Future[Unit]
  ): Future[Unit] = {
    provider.getDB.stream(userThingTags.query.result)

    if (limit > 0 && offset >= limit) {
      Future.unit
    } else {
      run {
        InhibitFilter(userThingTags.query)
          .filter(startingId)(id => _.userId >= id)
          .query
          .sortBy(_.id.asc.nullsFirst)
          .drop(offset)
          .take(perPage)
          .result
      }.flatMap {
        case x if x.isEmpty => Future.unit
        case x =>
          process(x).flatMap(
            _ =>
              loopThroughAllUserActions(offset + perPage, perPage, limit)(
                process
              )
          )
      }
    }
  }

  def insertOrUpdateAction(
    userId: String,
    thingId: Either[UUID, Slug],
    action: UserThingTagType,
    value: Option[Double]
  ): Future[Option[UserThingTag]] = {
    run {
      withThingId(thingId).flatMap {
        case None =>
          DBIO.failed(
            new IllegalArgumentException(s"Thing not found: $thingId")
          )
        case Some(id) =>
          userThingTags.query
            .filter(utt => {
              utt.userId === userId && utt.thingId === id && utt.action === action
            })
            .take(1)
            .result
            .headOption
            .flatMap {
              case Some(existing) =>
                val newTag = existing.copy(value = value)
                userThingTags.query.update(newTag).map {
                  case 1 => Some(newTag)
                  case _ => None
                }

              case None =>
                val tag = UserThingTag(
                  -1,
                  userId,
                  id,
                  action,
                  value
                )
                (userThingTags.query += tag).map(_ => Some(tag))
            }
      }
    }
  }

  private def addRemoveWhenWatchedRule(list: TrackedListRow) = {
    list.copy(
      rules = list.rules.map(
        rules =>
          rules.copy(
            rules = (rules.rules :+ DynamicListTagRule.notWatched).distinct
          )
      )
    )
  }

  private def removeRemoveWhenWatchedRule(list: TrackedListRow) = {
    list.copy(
      rules = list.rules.map(
        rules =>
          rules.copy(
            rules = (rules.rules
              .filterNot(_ == DynamicListTagRule.notWatched))
              .distinct
          )
      )
    )
  }

  def removeAction(
    userId: String,
    thingId: Either[UUID, Slug],
    action: UserThingTagType
  ): Future[Int] = {
    run {
      withThingId(thingId).flatMap {
        case None => DBIO.successful(0)
        case Some(id) =>
          userThingTags.query
            .filter(utt => {
              utt.userId === userId && utt.thingId === id && utt.action === action
            })
            .delete
      }
    }
  }
}

case class SlickDBNoAvailableThreadsException(message: String)
    extends Exception(message)

case class ListUpdateResult(
  preMutationRules: Option[DynamicListRules],
  postMutationRules: Option[DynamicListRules],
  preMutationOptions: Option[TrackedListRowOptions],
  postMutationOptions: Option[TrackedListRowOptions]) {

  def rulesChanged: Boolean = preMutationRules != postMutationRules
  def optionsChanged: Boolean = preMutationOptions != postMutationOptions

}
