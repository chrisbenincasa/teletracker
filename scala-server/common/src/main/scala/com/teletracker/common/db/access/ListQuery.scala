package com.teletracker.common.db.access

import com.teletracker.common.db.model._
import com.teletracker.common.db.util.InhibitFilter
import com.teletracker.common.inject.{DbImplicits, DbProvider}
import com.teletracker.common.util.{Field, ListFilters}
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListQuery @Inject()(
  val provider: DbProvider,
  val users: Users,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val userThingTags: UserThingTags,
  dynamicListBuilder: DynamicListBuilder,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext)
    extends DbAccess {
  import dbImplicits._
  import provider.driver.api._
  import slick.lifted.Shape._

  type ListsQuery =
    Query[trackedLists.TrackedListsTable, TrackedListRow, Seq]

  type ThingByListQuery =
    Query[(Rep[Int], things.ThingsTableRaw), (Int, ThingRaw), Seq]

  type ThingTagsByThingQuery =
    Query[
      (Rep[Int], userThingTags.UserThingTagsTable),
      (Int, UserThingTag),
      Seq
    ]

  private val defaultFields = List(Field("id"))

  def findUsersLists(
    userId: Int,
    includeThings: Boolean = true,
    includeThingMetadata: Boolean = false,
    selectThingFields: Option[List[Field]] = None
  ): Future[Seq[TrackedList]] = {
    val listsQuery = makeListsQuery(userId)

    val (thingsAction, thingCountAction) = if (includeThings) {
      makeThingsForListQuery(listsQuery, None).result -> DBIO
        .successful(Seq.empty)
    } else {
      val countByListIdQuery = makeThingsForListQuery(
        listsQuery,
        None
      ).distinctOn(_._2.id)
        .groupBy(_._1)
        .map {
          case (listId, group) => listId -> group.length
        }
        .result

      DBIO.successful(Seq.empty) -> countByListIdQuery
    }

    val listsF = run(listsQuery.result)
    val thingsF = run(thingsAction)
    val thingCountF = run(thingCountAction)

    for {
      lists <- listsF
      things <- thingsF
      thingCount <- thingCountF
    } yield {
      val countByListId = thingCount.toMap
      val thingsByListId = things.groupBy(_._1).mapValues(_.map(_._2))

      lists.map(list => {
        val count = countByListId.getOrElse(list.id, 0)

        list.toFull
          .applyIf(includeThings)(
            _.withThings(
              thingsByListId
                .getOrElse(list.id, Seq.empty)
                .map(_.selectFields(selectThingFields, defaultFields).toPartial)
                .toList
            )
          )
          .applyIf(!includeThings)(_.withCount(count))
      })
    }
  }

  def findList(
    userId: Int,
    listId: Int,
    includeMetadata: Boolean = true,
    includeTags: Boolean = true,
    selectFields: Option[List[Field]] = None,
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None
  ): Future[Option[(TrackedListRow, Seq[(ThingRaw, Seq[UserThingTag])])]] = {
    val typeFilters = filters.flatMap(_.itemTypes)

    def materialize(
      listOrQuery: Either[ListsQuery, TrackedListRow]
    ): Future[Option[(TrackedListRow, Seq[(ThingRaw, Seq[UserThingTag])])]] = {
      val thingsQuery = listOrQuery match {
        case Left(query) =>
          makeThingsForListQuery(query, typeFilters)
        case Right(list) =>
          makeThingsForListQuery(list.id, typeFilters)
      }

      val thingTagsQuery =
        makeUserThingTagQuery(userId, thingsQuery.map(_._2.id), includeTags)

      val thingsFut = run(thingsQuery.map {
        case (listId, thing) =>
          listId -> thing.projWithMetadata(includeMetadata)
      }.result)

      val thingTagsFut = run(thingTagsQuery.result)

      val listFut = listOrQuery match {
        case Left(query) => run(query.result.headOption)
        case Right(list) => Future.successful(Some(list))
      }

      for {
        listOpt <- listFut
        things <- thingsFut
        thingTags <- thingTagsFut
      } yield {
        listOpt.map(list => {
          val validThings = things.collect {
            case (listId, thing) if listId == list.id => thing
          }
          val tagsByThingId = thingTags.groupBy(_._1).mapValues(_.map(_._2))
          val thingAndActions = validThings.map(thing => {
            thing -> tagsByThingId.getOrElse(thing.id, Seq.empty)
          })

          list -> thingAndActions
        })
      }
    }

    val listAndThingsFut = isDynamicHint match {
      case Some(false) =>
        val listQuery = makeListsQuery(userId, Some(listId))

        materialize(Left(listQuery))

      case _ =>
        run {
          makeListsQuery(userId, Some(listId), includeDynamic = true).result.headOption
        }.flatMap {
          case None =>
            Future.successful(None)

          case Some(list) if list.isDynamic =>
            run(
              dynamicListBuilder
                .buildList(userId, list)
                .map(list -> _)
                .map(Option(_))
            )

          case Some(list) =>
            materialize(Right(list))
        }
    }

    listAndThingsFut.map {
      case Some((list, thingsAndActions)) =>
        val newThings = thingsAndActions.map {
          case (thing, actions) =>
            thing.selectFields(selectFields, defaultFields) -> actions
        }

        Some(list -> newThings)

      case listAndThings => listAndThings
    }
  }

  private def makeListsQuery(
    userId: Int,
    listId: Option[Int] = None,
    includeDynamic: Boolean = false
  ): ListsQuery = {
    InhibitFilter(
      trackedLists.query.filter(l => l.userId === userId && l.deletedAt.isEmpty)
    ).filter(listId)(id => _.id === id)
      .cond(!includeDynamic)(!_.isDynamic)
      .query
  }

  private def makeThingsForListQuery(
    listsQuery: ListsQuery,
    thingTypeFilter: Option[Set[ThingType]]
  ) = {
    val thingQuery = InhibitFilter(things.rawQuery)
      .filter(thingTypeFilter)(types => _.`type` inSetBind types)
      .query

    for {
      lists <- listsQuery
      tlt <- trackedListThings.query if lists.id === tlt.listId
      thing <- thingQuery if thing.id === tlt.thingId
    } yield {
      tlt.listId -> thing
    }
  }

  private def makeThingsForListQuery(
    listId: Int,
    thingTypeFilter: Option[Set[ThingType]]
  ) = {
    val thingQuery = InhibitFilter(things.rawQuery)
      .filter(thingTypeFilter)(types => _.`type` inSetBind types)
      .query

    for {
      tlt <- trackedListThings.query.filter(_.listId === listId)
      thing <- thingQuery if thing.id === tlt.thingId
    } yield {
      tlt.listId -> thing
    }
  }

  private def makeUserThingTagQuery(
    userId: Int,
    thingQuery: Query[Rep[UUID], UUID, Seq],
    includeTags: Boolean
  ) = {
    for {
      thingId <- thingQuery
      tags <- userThingTags.query
      if LiteralColumn(includeTags) && (thingId === tags.thingId && tags.userId === userId)
    } yield {
      thingId -> tags
    }
  }
}

object ListQuery {}
