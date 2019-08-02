package com.teletracker.service.api

import com.teletracker.service.db.access.{ListsDbAccess, UsersDbAccess}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ListsApi @Inject()(
  usersDbAccess: UsersDbAccess,
  listsDbAccess: ListsDbAccess
)(implicit executionContext: ExecutionContext) {
  def deleteList(
    userId: Int,
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
    userId: Int,
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
