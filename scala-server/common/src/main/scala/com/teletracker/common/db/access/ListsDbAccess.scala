package com.teletracker.common.db.access

import com.teletracker.common.db.{
  BaseDbProvider,
  DbImplicits,
  DbMonitoring,
  SyncDbProvider
}
import com.teletracker.common.db.model.{
  TrackedListRow,
  TrackedListThing,
  TrackedListThings,
  TrackedLists
}
import com.teletracker.common.util.GeneralizedDbFactory
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListsDbAccess @Inject()(
  val provider: BaseDbProvider,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {

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
    userId: String,
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
    userId: String,
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
    thingIds: Set[UUID]
  ) = {
    if (thingIds.isEmpty) {
      Future.successful(None)
    } else {
      run {
        val now = OffsetDateTime.now()
        trackedListThings.query ++= thingIds.map(thingId => {
          TrackedListThing(listId, thingId, now)
        })
      }
    }
  }
}
