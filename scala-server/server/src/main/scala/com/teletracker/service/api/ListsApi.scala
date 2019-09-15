package com.teletracker.service.api

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.api.model.TrackedListRules
import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.{ListsDbAccess, UsersDbAccess}
import com.teletracker.common.db.model.TrackedListRow
import com.teletracker.common.util.{FactoryImplicits, GeneralizedDbFactory}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListsApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess
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
}