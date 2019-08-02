package com.teletracker.service.db.access

import com.teletracker.service.db.model.{
  TrackedListRow,
  TrackedListThing,
  TrackedListThings,
  TrackedLists
}
import com.teletracker.service.inject.{DbImplicits, DbProvider}
import javax.inject.Inject
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

class ListsDbAccess @Inject()(
  val provider: DbProvider,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext)
    extends DbAccess {

  import provider.driver.api._

  def insertList(list: TrackedListRow) = {
    run {
      (trackedLists.query returning trackedLists.query.map(_.id)) += list
    }
  }

  def insertLists(lists: List[TrackedListRow]) = {
    if (lists.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        (trackedLists.query returning trackedLists.query.map(_.id)) ++= lists
      }
    }
  }

  def markListDeleted(
    userId: Int,
    listId: Int
  ) = {
    run {
      trackedLists
        .findSpecificListQuery(userId, listId)
        .map(_.map(_.deletedAt))
        .update(Some(OffsetDateTime.now()))
    }
  }

  def findItemsInList(
    userId: Int,
    listId: Int
  ) = {
    run {
      (trackedLists.query.filter(
        tl => tl.userId === userId && tl.id === listId
      ) joinLeft
        trackedListThings.query on (_.id === _.listId)).result
    }
  }

  def addTrackedThings(
    listId: Int,
    thingIds: Set[Int]
  ) = {
    if (thingIds.isEmpty) {
      Future.successful(None)
    } else {
      run {
        trackedListThings.query ++= thingIds.map(thingId => {
          TrackedListThing(listId, thingId)
        })
      }
    }
  }
}
