package com.teletracker.service.api

import com.teletracker.common.db.dynamo.model.{
  StoredUserList,
  StoredUserListFactory,
  StoredUserPreferences,
  StoredUserPreferencesBlob
}
import com.teletracker.common.db.dynamo.{
  MetadataDbAccess,
  ListsDbAccess => DynamoListsDbAccess
}
import com.teletracker.common.db.model.UserThingTagType
import com.teletracker.common.db.{Bookmark, DefaultForListType, SortMode}
import com.teletracker.common.elasticsearch.model.EsItemTag
import com.teletracker.common.elasticsearch.{
  DynamicListBuilder,
  ItemUpdater,
  ListBuilder
}
import com.teletracker.common.util.{IdOrSlug, ListFilters, NetworkCache}
import com.teletracker.service.api.model._
import com.teletracker.common.util.Functions._
import com.teletracker.service.api.converters.UserListConverter
import com.teletracker.service.controllers.UpdateUserRequestPayload
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UsersApi @Inject()(
  listBuilder: ListBuilder,
  dynamicListBuilder: DynamicListBuilder,
  listsApi: ListsApi,
  dynamoListsDbAccess: DynamoListsDbAccess,
  metadataDbAccess: MetadataDbAccess,
  networkCache: NetworkCache,
  userListConverter: UserListConverter
)(implicit executionContext: ExecutionContext) {

  def getUser(userId: String): Future[UserDetails] = {
    val networksFut = networkCache.getAllNetworks()
    val metadataFut = metadataDbAccess.getUserPreferences(userId)

    for {
      meta <- metadataFut
      nets <- networksFut
    } yield {
      meta match {
        case Some(value) =>
          UserDetails(
            userId,
            UserPreferences(
              presentationTypes = value.preferences.presentationTypes,
              showOnlyNetworkSubscriptions =
                value.preferences.showOnlyNetworkSubscriptions,
              hideAdultContent = value.preferences.hideAdultContent
            ),
            networkPreferences = value.preferences.networkIds
              .map(_.flatMap(id => nets.find(_.id == id)))
              .map(_.toList.map(Network.fromStoredNetwork))
              .getOrElse(Nil)
          )
        case None =>
          UserDetails(userId, UserPreferences(), Nil)
      }
    }
  }

  def updateUser(
    userId: String,
    updateUserRequest: UpdateUserRequestPayload
  ): Future[Unit] = {
    val networksFut = networkCache.getAllNetworks()
    val preferencesFut = metadataDbAccess.getUserPreferences(userId)

    for {
      networks <- networksFut
      preferencesOpt <- preferencesFut
    } yield {
      preferencesOpt match {
        case Some(preferences) =>
          val newPreferences = preferences
            .through(
              _.applyOptional(updateUserRequest.networkSubscriptions)(
                (prefs, networkSubscriptions) => {
                  val networkIds = networkSubscriptions.flatMap(_.id).toSet
                  val existingNetworkIds =
                    preferences.preferences.networkIds.getOrElse(Set.empty)

                  val toDelete =
                    existingNetworkIds.diff(networkIds)

                  val toAdd = networkSubscriptions
                    .filter(
                      net => net.id.isDefined && !existingNetworkIds(net.id.get)
                    )
                    .flatMap(_.id)
                    .toSet

                  prefs.copy(
                    preferences = prefs.preferences.copy(
                      networkIds =
                        Some((existingNetworkIds -- toDelete) ++ toAdd)
                    )
                  )
                }
              )
            )
            .through(
              _.applyOptional(updateUserRequest.userPreferences)(
                (prefs, updatePrefs) => {
                  prefs.copy(
                    preferences = prefs.preferences.copy(
                      presentationTypes =
                        Some(updatePrefs.presentationTypes).filter(_.nonEmpty),
                      showOnlyNetworkSubscriptions =
                        updatePrefs.showOnlyNetworkSubscriptions,
                      hideAdultContent = updatePrefs.hideAdultContent
                    )
                  )
                }
              )
            )

          metadataDbAccess.saveUserPreferences(
            newPreferences.copy(lastUpdatedAt = OffsetDateTime.now())
          )

        case None =>
          val now = OffsetDateTime.now()

          StoredUserPreferences(
            userId = userId,
            createdAt = now,
            lastUpdatedAt = now,
            preferences = StoredUserPreferencesBlob(
              networkIds = updateUserRequest.networkSubscriptions
                .map(_.flatMap(_.id).toSet),
              presentationTypes =
                updateUserRequest.userPreferences.map(_.presentationTypes),
              showOnlyNetworkSubscriptions = updateUserRequest.userPreferences
                .flatMap(_.showOnlyNetworkSubscriptions),
              hideAdultContent =
                updateUserRequest.userPreferences.flatMap(_.hideAdultContent)
            )
          )
      }
    }
  }

  def getUserLists(userId: String): Future[Seq[UserList]] = {
    dynamoListsDbAccess
      .getAllListsForUser(userId)
      .flatMap(lists => {
        val (dynamicLists, regularLists) = lists.partition(_.isDynamic)

        for {
          regularListCounts <- if (regularLists.nonEmpty) {
            listBuilder
              .getRegularListsCounts(userId, regularLists.map(_.id).toList)
              .map(_.toMap)
          } else {
            Future.successful(Map.empty[UUID, Long])
          }
          dynamicListCounts <- if (dynamicLists.nonEmpty) {
            dynamicListBuilder
              .getDynamicListCounts(userId, dynamicLists)
              .map(_.toMap)
          } else {
            Future.successful(Map.empty[UUID, Long])
          }
        } yield {

          lists.map(list => {
            val count =
              if (list.isDynamic) dynamicListCounts.getOrElse(list.id, 0L).toInt
              else regularListCounts.getOrElse(list.id, 0L).toInt

            userListConverter
              .fromStoredList(list)
              .withCount(count)
          })
        }
      })
  }

  def getUserList(
    listId: IdOrSlug,
    userId: Option[String],
    mustBePublic: Boolean,
    includeItems: Boolean
  ): Future[Option[UserList]] = {
    val listFut = listId.idOrSlug match {
      case Left(id)    => listsApi.findListForUser(id, userId)
      case Right(slug) => listsApi.findListViaAlias(slug, None)
    }

    listFut.flatMap {
      case None => Future.successful(None)

      case Some(list) =>
        val countFut = if (list.isDynamic) {
          dynamicListBuilder
            .getDynamicListCounts(list.userId, List(list))
            .map(_.toMap.getOrElse(list.id, 0L))
        } else {
          listBuilder
            .getRegularListsCounts(list.userId, List(list.id))
            .map(_.toMap.getOrElse(list.id, 0L))
        }

        countFut
          .map(
            count =>
              Some(
                userListConverter
                  .fromStoredList(list)
                  .withCount(Math.max(Int.MaxValue.toLong, count).toInt)
              )
          )
    }
  }

  def getUserListAndItems(
    listId: IdOrSlug,
    requestingUserId: Option[String],
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None,
    sortMode: SortMode = DefaultForListType(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 10,
    mustBePublic: Boolean = false
  ): Future[Option[(UserList, Option[Bookmark])]] = {
    val listFut = listId.idOrSlug match {
      case Left(id)    => listsApi.findListForUser(id, requestingUserId)
      case Right(slug) => listsApi.findListViaAlias(slug, None)
    }

    listFut.flatMap {
      case None =>
        Future.successful(None)

      case Some(list) if !requestingUserCanViewList(list, requestingUserId) =>
        Future.successful(None)

      case Some(list) if list.isDynamic =>
        dynamicListBuilder
          .buildDynamicList(
            list.userId,
            list,
            filters,
            sortMode,
            bookmark,
            limit = limit
          )
          .map {
            case (listThings, count, peopleFromRules) => {
              userListConverter
                .fromStoredList(list)
                .withItems(
                  listThings.items
                    .map(Item.fromEsItem(_))
                    .map(_.scopeToUser(requestingUserId))
                )
                .withPeople(peopleFromRules.map(Person.fromEsPerson(_, None)))
                .withCount(count.toInt) -> listThings.bookmark
            }
          }
          .map(Some(_))

      case Some(list) if !list.isDynamic =>
        listBuilder
          .buildRegularList(
            list.userId,
            list,
            filters,
            sortMode,
            bookmark,
            limit = limit
          )
          .map {
            case (listThings, count) => {
              userListConverter
                .fromStoredList(list)
                .withItems(
                  listThings.items
                    .map(Item.fromEsItem(_))
                    .map(_.scopeToUser(requestingUserId))
                )
                .withCount(count.toInt) -> listThings.bookmark
            }
          }
          .map(Some(_))
    }
  }

  private def requestingUserCanViewList(
    list: StoredUserList,
    requestingUserId: Option[String]
  ) = {
    requestingUserId match {
      case Some(value) =>
        list.isPublic || list.userId == value

      case None =>
        list.isPublic
    }
  }

  def createDefaultListsForUser(userId: String): Future[Unit] = {
    Future
      .sequence(
        List(
          dynamoListsDbAccess
            .saveList(StoredUserListFactory.defaultList(userId)),
          dynamoListsDbAccess
            .saveList(StoredUserListFactory.watchedList(userId))
        )
      )
      .map(_ => Unit)
  }

  def handleTagChange(
    itemId: UUID,
    userThingTag: EsItemTag
  ): Future[Unit] = {
    userThingTag match {
      case EsItemTag.UserScoped(userId, UserThingTagType.Watched, _, _, _) =>
        listsApi
          .findUserLists(userId)
          .flatMap(lists => {
            // Don't have to handle dynamic lists. Exclusions are generated at query time.
            val deleteOnWatchLists = lists
              .filter(_.configuration.options.exists(_.removeWatchedItems))
              .filter(!_.isDynamic)

            if (deleteOnWatchLists.isEmpty) {
              Future.unit
            } else {
              Future
                .sequence {
                  deleteOnWatchLists.map(list => {
                    listsApi.removeThingFromList(userId, list.id, itemId)
                  })
                }
                .map(_ => {})
            }
          })

      case _ => Future.unit
    }
  }
}
