package com.teletracker.service.api

import com.teletracker.common.db.access.{ListsDbAccess, UsersDbAccess}
import com.teletracker.common.db.model.{
  TrackedListFactory,
  UserPreferences,
  UserThingTag,
  UserThingTagType
}
import com.teletracker.common.db.{Bookmark, DefaultForListType, SortMode}
import com.teletracker.common.elasticsearch.{
  DynamicListBuilder,
  EsItemTag,
  ItemUpdater,
  ListBuilder
}
import com.teletracker.common.util.ListFilters
import com.teletracker.service.api.model.{Item, Person, UserDetails, UserList}
import com.teletracker.service.controllers.UpdateUserRequestPayload
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UsersApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess,
  listBuilder: ListBuilder,
  dynamicListBuilder: DynamicListBuilder,
  listsApi: ListsApi,
  itemUpdater: ItemUpdater
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

  def registerUser(userId: String): Future[Seq[Int]] = {
    createDefaultListsForUser(userId)
  }

  // ES only
  def getUserLists(userId: String): Future[Seq[UserList]] = {
    usersDbAccess
      .findListsForUserRaw(userId)
      .flatMap(lists => {
        val (dynamicLists, regularLists) = lists.partition(_.isDynamic)

        for {
          regularListCounts <- if (regularLists.nonEmpty) {
            listBuilder
              .getRegularListsCounts(userId, regularLists.map(_.id).toList)
              .map(_.toMap)
          } else {
            Future.successful(Map.empty[Int, Long])
          }
          dynamicListCounts <- if (dynamicLists.nonEmpty) {
            dynamicListBuilder
              .getDynamicListCounts(userId, dynamicLists.toList)
              .map(_.toMap)
          } else {
            Future.successful(Map.empty[Int, Long])
          }
        } yield {

          lists.map(list => {
            val count =
              if (list.isDynamic) dynamicListCounts.getOrElse(list.id, 0L).toInt
              else regularListCounts.getOrElse(list.id, 0L).toInt

            UserList
              .fromRow(list)
              .withCount(count)
          })
        }
      })
  }

  def getUserList(
    userId: String,
    listId: Int,
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None,
    sortMode: SortMode = DefaultForListType(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 10
  ): Future[Option[(UserList, Option[Bookmark])]] = {
    usersDbAccess.getList(userId, listId).flatMap {
      case None =>
        Future.successful(None)

      case Some(list) if list.isDynamic =>
        dynamicListBuilder
          .buildDynamicList(
            userId,
            list,
            filters,
            sortMode,
            bookmark,
            limit = limit
          )
          .map {
            case (listThings, count, peopleFromRules) => {
              UserList
                .fromRow(list)
                .withItems(
                  listThings.items
                    .map(Item.fromEsItem(_))
                    .map(_.scopeToUser(userId))
                )
                .withPeople(peopleFromRules.map(Person.fromEsPerson(_, None)))
                .withCount(count.toInt) -> listThings.bookmark
            }
          }
          .map(Some(_))

      case Some(list) if !list.isDynamic =>
        listBuilder
          .buildRegularList(
            userId,
            list,
            filters,
            sortMode,
            bookmark,
            limit = limit
          )
          .map {
            case (listThings, count) => {
              UserList
                .fromRow(list)
                .withItems(
                  listThings.items
                    .map(Item.fromEsItem(_))
                    .map(_.scopeToUser(userId))
                )
                .withCount(count.toInt) -> listThings.bookmark
            }
          }
          .map(Some(_))
    }
  }

  def createDefaultListsForUser(userId: String): Future[Seq[Int]] = {
    listsDbAccess.insertLists(
      List(
        TrackedListFactory.defaultList(userId),
        TrackedListFactory.watchedList(userId)
      )
    )
  }

  def handleTagChange(userThingTag: UserThingTag): Future[Unit] = {
    userThingTag.action match {
      case UserThingTagType.Watched =>
        listsDbAccess
          .findRemoveOnWatchedLists(userThingTag.userId)
          .flatMap(lists => {
            usersDbAccess.removeThingFromLists(
              lists.filter(!_.isDynamic).map(_.id).toSet,
              userThingTag.thingId
            )
          })
          .map(_ => {})

      case _ =>
        Future.unit
    }
  }

  def handleTagChange(
    itemId: UUID,
    userThingTag: EsItemTag
  ): Future[Unit] = {
    userThingTag match {
      case EsItemTag.UserScoped(userId, UserThingTagType.Watched, _, _) =>
        listsDbAccess
          .findRemoveOnWatchedLists(userId)
          .flatMap(lists => {
            itemUpdater.removeItemFromLists(
              lists.filter(!_.isDynamic).map(_.id).toSet,
              userId
            )
          })
          .map(_ => {})

      case _ => Future.unit
    }
  }
}
