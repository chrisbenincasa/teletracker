package com.teletracker.service.api

import com.teletracker.common.api.model.TrackedListRules
import com.teletracker.common.db.access.{
  ListUpdateResult,
  ListsDbAccess,
  UsersDbAccess
}
import com.teletracker.common.db.model.TrackedListRow
import com.teletracker.common.elasticsearch.{
  ItemUpdater,
  PersonLookup,
  UpdateMultipleDocResponse
}
import com.teletracker.common.util.Slug
import com.teletracker.service.api.model.{UserListPersonRule, UserListRules}
import javax.inject.Inject
import org.elasticsearch.action.update.UpdateResponse
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListsApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext) {
  def createList(
    userId: String,
    name: String,
    thingsToAdd: Option[List[UUID]],
    rules: Option[TrackedListRules]
  ): Future[TrackedListRow] = {
    if (thingsToAdd.isDefined && rules.isDefined) {
      Future.failed(
        new IllegalArgumentException(
          "Cannot specify both thingIds and rules when creating a list"
        )
      )
    } else {
      usersDbAccess
        .insertList(userId, name, rules.map(_.toRow))
        .flatMap(newList => {
          thingsToAdd
            .map(things => {
              listsDbAccess
                .addTrackedThings(newList.id, things.toSet)
                .map(_ => newList)
            })
            .getOrElse(Future.successful(newList))
        })
    }
  }

  def createList2(
    userId: String,
    name: String,
    thingsToAdd: Option[List[UUID]],
    rules: Option[UserListRules]
  ): Future[TrackedListRow] = {
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

        usersDbAccess
          .insertList(userId, name, newRules.map(_.toRow))
          .flatMap(newList => {
            thingsToAdd
              .map(things => {
                listsDbAccess
                  .addTrackedThings(newList.id, things.toSet)
                  .map(_ => newList)
              })
              .getOrElse(Future.successful(newList))
          })
      })

    }
  }

  def deleteList(
    userId: String,
    listId: Int,
    mergeWithList: Option[Int]
  ): Future[Boolean] = {
    mergeWithList
      .map(listId => {
        usersDbAccess
          .getList(
            userId,
            listId
          )
      })
      .getOrElse(Future.successful(None))
      .flatMap {
        case Some(list) if list.isDynamic =>
          Future.failed(new IllegalArgumentException)

        case None if mergeWithList.nonEmpty =>
          Future.failed(new IllegalArgumentException)

        case _ =>
          listsDbAccess.markListDeleted(userId, listId).flatMap {
            case 0 => Future.successful(false)

            case 1 if mergeWithList.isDefined =>
              mergeLists(userId, listId, mergeWithList.get).map(_ => true)

            case 1 => Future.successful(true)

            case _ => throw new IllegalStateException("")
          }
      }

  }

  // ES only
  def addThingToList(
    userId: String,
    listId: Int,
    thingId: UUID
  ): Future[UpdateMultipleDocResponse] = {
    itemUpdater.addListTagToItem(thingId, listId, userId)
  }

  // ES Only
  def removeThingFromList(
    userId: String,
    listId: Int,
    thingId: UUID
  ): Future[UpdateMultipleDocResponse] = {
    itemUpdater.removeListTagFromItem(thingId, listId, userId)
  }

  def mergeLists(
    userId: String,
    sourceList: Int,
    targetList: Int
  ): Future[Unit] = {

    val sourceItemsFut =
      listsDbAccess.findItemsInList(userId, sourceList).map(_.flatMap(_._2))
    val targetItemsFut =
      listsDbAccess.findItemsInList(userId, targetList).map(_.flatMap(_._2))

    for {
      sourceItems <- sourceItemsFut
      targetItems <- targetItemsFut
      sourceIds = sourceItems.map(_.thingId)
      targetIds = targetItems.map(_.thingId)
      idsToInsert = sourceIds.toSet -- targetIds
      _ <- listsDbAccess.addTrackedThings(targetList, idsToInsert)
    } yield {}
  }

  def handleListUpdateResult(
    userId: String,
    listId: Int,
    listUpdateResult: ListUpdateResult
  ): Future[Unit] = {
    if (listUpdateResult.optionsChanged) {
      (
        listUpdateResult.preMutationOptions,
        listUpdateResult.postMutationOptions
      ) match {
        case (preOpts, Some(opts))
            if (preOpts.isEmpty || !preOpts.get.removeWatchedItems) && opts.removeWatchedItems =>
          listsDbAccess.removeWatchedThingsFromList(listId).map(_ => {})

        case _ => Future.unit
      }
    } else {
      Future.unit
    }
  }
}
