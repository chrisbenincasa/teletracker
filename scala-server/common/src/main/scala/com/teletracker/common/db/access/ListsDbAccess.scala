package com.teletracker.common.db.access

import com.teletracker.common.db.{BaseDbProvider, DbImplicits, DbMonitoring}
import com.teletracker.common.db.model.{
  TrackedListRow,
  TrackedListThing,
  TrackedListThings,
  TrackedLists,
  UserThingTagType,
  UserThingTags
}
import io.circe.Json
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListsDbAccess @Inject()(
  val provider: BaseDbProvider,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val userThingTags: UserThingTags,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {

  import provider.driver.api._
  import dbImplicits._

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
  ): Future[Option[Int]] = {
    if (thingIds.isEmpty) {
      Future.successful(None)
    } else {
      run {
        val now = OffsetDateTime.now()
        trackedListThings.query ++= thingIds.map(thingId => {
          TrackedListThing(listId, thingId, now, None)
        })
      }
    }
  }

  def findRemoveOnWatchedLists(userId: String): Future[Seq[TrackedListRow]] = {
    run {
      trackedLists.query
        .filter(_.userId === userId)
        .filter(_.deletedAt.isEmpty)
        .filter(list => {
          list.options.asColumnOf[Json] +> "removeWatchedItems" === Json
            .fromBoolean(true)
        })
        .result
    }
  }

  def removeWatchedThingsFromList(listId: Int): Future[Int] = {
//    val now = OffsetDateTime.now()
    run {
      val s = sqlu"""
        UPDATE list_things AS lt
        SET
            removed_at = current_timestamp
        FROM user_thing_tags AS utt
        WHERE 
            lt.objects_id = utt.thing_id AND
            utt.action = ${UserThingTagType.Watched.getName} AND
            lt.lists_id = $listId;
      """

      s.statements.foreach(println)
      s
//      (for {
//        tlt <- trackedListThings.query
//        utt <- userThingTags.query
//        if utt.action === UserThingTagType.Watched
//        if tlt.listId === listId
//        if utt.thingId === tlt.thingId
//      } yield {
//        tlt.removedAt
//      }).update(Some(now))
    }
  }
}
