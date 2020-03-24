package com.teletracker.service.api

import com.teletracker.common.api.model.{TrackedListOptions, TrackedListRules}
import com.teletracker.common.db.dynamo.model.{
  StoredUserList,
  UserListRowOptions
}
import com.teletracker.common.db.dynamo.{ListsDbAccess => DynamoListsDbAccess}
import com.teletracker.common.db.model.{DynamicListRules, DynamicListTagRule}
import com.teletracker.common.elasticsearch.{
  ItemUpdater,
  PersonLookup,
  UpdateMultipleDocResponse
}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.Functions._
import com.teletracker.service.api.converters.UserListConverter
import com.teletracker.service.api.model.{UserListPersonRule, UserListRules}
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListsApi @Inject()(
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup,
  dynamoListsDbAccess: DynamoListsDbAccess,
  userListConverter: UserListConverter
)(implicit executionContext: ExecutionContext) {
  def createList(
    userId: String,
    name: String,
    thingsToAdd: Option[List[UUID]],
    rules: Option[UserListRules]
  ): Future[StoredUserList] = {
    if (thingsToAdd.isDefined && rules.isDefined) {
      Future.failed(
        new IllegalArgumentException(
          "Cannot specify both thingIds and rules when creating a list"
        )
      )
    } else {
      val personSlugRulesToId = rules
        .map(_.rules)
        .map(listRules => {
          // TODO: Use cache first
          val slugsToResolve = listRules.collect {
            case UserListPersonRule(None, Some(personSlug), _) => personSlug
          }.toSet

          if (slugsToResolve.nonEmpty) {
            personLookup
              .lookupPeopleBySlugs(slugsToResolve.toList)
              .map(found => {
                if (slugsToResolve.size != found.keySet.size) {
                  throw new IllegalArgumentException(
                    s"Could not find people with slugs: ${slugsToResolve -- found.keySet}"
                  )
                }

                slugsToResolve.toList
                  .flatMap(slug => found.get(slug).map(slug -> _.id))
                  .toMap
              })
          } else {
            Future.successful(Map.empty[Slug, UUID])
          }
        })
        .getOrElse {
          Future.successful(Map.empty[Slug, UUID])
        }

      personSlugRulesToId.flatMap(slugToId => {
        val sanitizedRules = rules
          .map(_.rules)
          .map(listRules => {
            listRules.map {
              case listRule @ UserListPersonRule(None, Some(personSlug), _) =>
                listRule.copy(
                  personId = Some(slugToId.apply(personSlug)),
                  personSlug = None
                )

              case listRule => listRule
            }
          })
          .getOrElse(Nil)

        val newRules = rules.map(_.copy(rules = sanitizedRules))

        val now = OffsetDateTime.now()
        dynamoListsDbAccess
          .saveList(
            StoredUserList(
              id = UUID.randomUUID(),
              name = name,
              isDefault = false,
              isPublic = false,
              userId = userId,
              isDynamic = newRules.isDefined,
              rules = newRules.map(_.toRow),
              options = None,
              createdAt = Some(now),
              lastUpdatedAt = Some(now)
            )
          )
          .flatMap(list => {
            thingsToAdd
              .map(items => {
                Future
                  .sequence(items.map(addThingToList(userId, list.id, _)))
                  .map(_ => list)
              })
              .getOrElse(Future.successful(list))
          })
      })

    }
  }

  def deleteList(
    userId: String,
    listId: UUID,
    mergeWithList: Option[UUID]
  ): Future[Boolean] = {
    // TODO: merge list
    dynamoListsDbAccess.deleteList(listId, userId).map(_ >= 1)
  }

  def findUserLists(userId: String) = {
    dynamoListsDbAccess
      .getAllListsForUser(userId)
      .map(_.map(userListConverter.fromStoredList))
  }

  def findListForUser(
    listId: UUID,
    userId: Option[String]
  ): Future[Option[StoredUserList]] = {
    dynamoListsDbAccess.getList(listId).map {
      case None                                        => None
      case Some(list) if list.isPublic                 => Some(list)
      case Some(list) if !userId.contains(list.userId) => None
      case Some(list)                                  => Some(list)
    }
  }

  def findPublicList(listId: UUID): Future[Option[StoredUserList]] = {
    dynamoListsDbAccess.getList(listId).map(_.filter(_.isPublic))
  }

  def findListViaAlias(
    alias: Slug,
    expectedUser: Option[String]
  ): Future[Option[StoredUserList]] = {
    dynamoListsDbAccess.getListIdForAlias(alias.value).flatMap {
      case None => Future.successful(None)

      case Some(id) if expectedUser.isDefined =>
        findListForUser(id, expectedUser).map {
          case None                                              => None
          case Some(list) if !expectedUser.contains(list.userId) => None
          case Some(list)                                        => Some(list)
        }

      case Some(id) =>
        findPublicList(id)
    }
  }

  def findListForUserLegacy(
    listId: Int,
    userId: String
  ) = {
    dynamoListsDbAccess.getListByLegacyId(userId, listId)
  }

  def updateList(
    listId: UUID,
    userId: String,
    name: Option[String],
    rules: Option[TrackedListRules],
    options: Option[TrackedListOptions]
  ) = {
    val rulesDao = rules.map(_.toRow)
    val optionsDao = options.map(_.toDynamoRow)

    dynamoListsDbAccess.getList(listId).flatMap {
      case None =>
        Future.failed(
          new IllegalArgumentException(s"List with id = ${listId} not found")
        )
      case Some(list) =>
        val updatedList = list
          .copy(
            name = name.getOrElse(list.name),
            rules = rulesDao.orElse(list.rules),
            options = optionsDao.orElse(list.options),
            lastUpdatedAt = Some(OffsetDateTime.now())
          )
          .applyIf(options.exists(_.removeWatchedItems) && list.isDynamic)(
            addRemoveWhenWatchedRule
          )
          .applyIf(options.exists(!_.removeWatchedItems) && list.isDynamic)(
            removeRemoveWhenWatchedRule
          )

        dynamoListsDbAccess
          .saveList(updatedList)
          .map(list => {
            ListUpdateResult(
              list.rules,
              rulesDao.orElse(list.rules),
              list.options,
              optionsDao.orElse(list.options)
            )
          })
    }
  }

  private def addRemoveWhenWatchedRule(list: StoredUserList) = {
    list.copy(
      rules = list.rules.map(
        rules =>
          rules.copy(
            rules = (rules.rules :+ DynamicListTagRule.notWatched).distinct
          )
      )
    )
  }

  private def removeRemoveWhenWatchedRule(list: StoredUserList) = {
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

  // ES only
  def addThingToList(
    userId: String,
    listId: UUID,
    thingId: UUID
  ): Future[UpdateMultipleDocResponse] = {
    itemUpdater.addListTagToItem(thingId, listId, userId)
  }

  // ES Only
  def removeThingFromList(
    userId: String,
    listId: UUID,
    thingId: UUID
  ): Future[UpdateMultipleDocResponse] = {
    itemUpdater.removeListTagFromItem(thingId, listId, userId)
  }

//  def mergeLists(
//    userId: String,
//    sourceList: Int,
//    targetList: Int
//  ): Future[Unit] = {
//
//    val sourceItemsFut =
//      listsDbAccess.findItemsInList(userId, sourceList).map(_.flatMap(_._2))
//    val targetItemsFut =
//      listsDbAccess.findItemsInList(userId, targetList).map(_.flatMap(_._2))
//
//    for {
//      sourceItems <- sourceItemsFut
//      targetItems <- targetItemsFut
//      sourceIds = sourceItems.map(_.thingId)
//      targetIds = targetItems.map(_.thingId)
//      idsToInsert = sourceIds.toSet -- targetIds
//      _ <- listsDbAccess.addTrackedThings(targetList, idsToInsert)
//    } yield {}
//  }

  def handleListUpdateResult(
    userId: String,
    listId: UUID,
    listUpdateResult: ListUpdateResult
  ): Future[Unit] = {
    if (listUpdateResult.optionsChanged) {
      (
        listUpdateResult.preMutationOptions,
        listUpdateResult.postMutationOptions
      ) match {
        case (preOpts, Some(opts))
            if (preOpts.isEmpty || !preOpts.get.removeWatchedItems) && opts.removeWatchedItems =>
          // TODO: Implement with ES
          Future.unit
//          listsDbAccess.removeWatchedThingsFromList(listId).map(_ => {})

        case _ => Future.unit
      }
    } else {
      Future.unit
    }
  }
}

case class ListUpdateResult(
  preMutationRules: Option[DynamicListRules],
  postMutationRules: Option[DynamicListRules],
  preMutationOptions: Option[UserListRowOptions],
  postMutationOptions: Option[UserListRowOptions]) {

  def rulesChanged: Boolean = preMutationRules != postMutationRules
  def optionsChanged: Boolean = preMutationOptions != postMutationOptions

}
