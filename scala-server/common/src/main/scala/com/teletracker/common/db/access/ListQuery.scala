package com.teletracker.common.db.access

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.api.model.TrackedList
import com.teletracker.common.db.{
  AddedTime,
  BaseDbProvider,
  Bookmark,
  DbImplicits,
  DbMonitoring,
  DefaultForListType,
  Popularity,
  Recent,
  SortMode,
  SyncDbProvider
}
import com.teletracker.common.db.model._
import com.teletracker.common.db.util.InhibitFilter
import com.teletracker.common.util.{Field, GeneralizedDbFactory, ListFilters}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.json.circe.Paths
import javax.inject.Inject
import slick.lifted.ColumnOrdered
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ListQuery @Inject()(
  val provider: BaseDbProvider,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val userThingTags: UserThingTags,
  dynamicListBuilder: DynamicListBuilder,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {
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
    userId: String,
    includeThings: Boolean = true,
    includeThingMetadata: Boolean = false,
    selectThingFields: Option[List[Field]] = None
  ): Future[Seq[TrackedList]] = {
    val listsQuery = makeListsQuery(userId, includeDynamic = true)

    val (thingsAction, thingCountAction) = if (includeThings) {
      makeThingsForListQuery(listsQuery, None, None).result -> DBIO
        .successful(Seq.empty)
    } else {
      val countByListIdQuery = countThingsForStandardListQuery(
        listsQuery,
        None
      ).result

      val countByDynamicListQuery = listsQuery
        .filter(_.isDynamic)
        .map(_.id)
        .result
        .flatMap(ids => {
          dynamicListBuilder.countMatchingThings(ids.toSet)
        })

      val both = for {
        countA <- countByListIdQuery
        countB <- countByDynamicListQuery
      } yield countA ++ countB

      DBIO.successful(Seq.empty) -> both
    }

    val listsF = run("findUsersLists_lists")(listsQuery.result)
    val thingsF = run("findUsersLists_things")(thingsAction)
    val thingCountF = run("findUsersLists_thing")(thingCountAction)

    for {
      lists <- listsF
      things <- thingsF
      thingCount <- thingCountF
    } yield {
      val countByListId = thingCount.toMap
      val thingsByListId = things.groupBy(_._1).mapValues(_.map(_._3))

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
    userId: String,
    listId: Int,
    includeMetadata: Boolean = true,
    includeTags: Boolean = true,
    selectFields: Option[List[Field]] = None,
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None,
    sortMode: SortMode = DefaultForListType()
  ): Future[ListQueryResult] = {
    val typeFilters = filters.flatMap(_.itemTypes)

    def buildBookmark(
      thing: ThingRaw,
      addedAt: OffsetDateTime,
      owningListIsDynamic: Boolean,
      sortMode: SortMode
    ): Bookmark = {
      sortMode match {
        case _: Popularity =>
          Bookmark(
            sortMode.`type`,
            sortMode.isDesc,
            thing.popularity.getOrElse(0.0).toString
          )
        case _: Recent =>
          Bookmark(
            sortMode.`type`,
            sortMode.isDesc,
            thing.metadata
              .flatMap(Paths.releaseDate)
              .getOrElse(LocalDate.MIN.toString)
          )
        case _: AddedTime =>
          Bookmark(sortMode.`type`, sortMode.isDesc, addedAt.toString)
        case d: DefaultForListType =>
          buildBookmark(
            thing,
            addedAt,
            owningListIsDynamic,
            d.get(owningListIsDynamic)
          )
      }
    }

    def materialize(
      listOrQuery: Either[ListsQuery, TrackedListRow]
    ): Future[Option[
      (TrackedListRow, Seq[(ThingRaw, OffsetDateTime, Seq[UserThingTag])])
    ]] = {
      val thingsQuery = listOrQuery match {
        case Left(query) =>
          makeThingsForListQuery(query, typeFilters, Some(sortMode))
        case Right(list) =>
          makeThingsForListQuery(list.id, typeFilters, Some(sortMode))
      }

      val thingTagsQuery =
        makeUserThingTagQuery(userId, thingsQuery.map(_._3.id), includeTags)

      val thingsFut = run("findList_things")(thingsQuery.map {
        case (listId, addedAt, thing) =>
          (listId, addedAt, thing.projWithMetadata(includeMetadata))
      }.result)

      val thingTagsFut = run("findList_thingTags")(thingTagsQuery.result)

      val listFut = listOrQuery match {
        case Left(query) => run("findList_list")(query.result.headOption)
        case Right(list) => Future.successful(Some(list))
      }

      for {
        listOpt <- listFut
        things <- thingsFut
        thingTags <- thingTagsFut
      } yield {
        listOpt.map(list => {
          val validThingsAndAddedTime = things.collect {
            case (listId, addedAt, thing) if listId == list.id =>
              thing -> addedAt
          }

          val tagsByThingId = thingTags.groupBy(_._1).mapValues(_.map(_._2))
          val thingAndActions = validThingsAndAddedTime.map {
            case (thing, addedAt) => {
              (thing, addedAt, tagsByThingId.getOrElse(thing.id, Seq.empty))
            }
          }

          list -> thingAndActions
        })
      }
    }

    val listAndThingsFut = isDynamicHint match {
      case Some(false) =>
        val listQuery = makeListsQuery(userId, Some(listId))

        materialize(Left(listQuery))

      case _ =>
        run("findList_dynamic") {
          makeListsQuery(userId, Some(listId), includeDynamic = true).result.headOption
        }.flatMap {
          case None =>
            Future.successful(None)

          case Some(list) if list.isDynamic =>
            run("findList_dynamicThings")(
              dynamicListBuilder
                .buildList(userId, list, sortMode)
                .map(list -> _)
                .map(Option(_))
            ).map(_.map {
              case (list, things) =>
                list -> things.map {
                  case (thing, actions) =>
                    (thing, OffsetDateTime.now(), actions)
                }
            })

          case Some(list) =>
            materialize(Right(list))
        }
    }

    listAndThingsFut
      .map(_.map {
        case (list, thingsActionsAddedTime) =>
          val things = thingsActionsAddedTime.map {
            case (thing, _, actions) =>
              thing
                .selectFields(selectFields, defaultFields)
                .toPartial
                .withUserMetadata(UserThingDetails(Seq.empty, actions))
          }.toList

          val bookmark = thingsActionsAddedTime.lastOption
            .map {
              case (thing, addedAt, _) =>
                buildBookmark(thing, addedAt, list.isDynamic, sortMode)
            }

          ListQueryResult(Some(list.toFull.withThings(things)), bookmark)
      }.getOrElse(ListQueryResult.empty))
  }

  private def makeListsQuery(
    userId: String,
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
    thingTypeFilter: Option[Set[ThingType]],
    sortMode: Option[SortMode]
  ) = {
    val thingQuery = InhibitFilter(things.rawQuery)
      .filter(thingTypeFilter)(types => _.`type` inSetBind types)
      .query

    val q = (for {
      lists <- listsQuery
      tlt <- trackedListThings.query if lists.id === tlt.listId
      thing <- thingQuery if thing.id === tlt.thingId
    } yield {
      (tlt.listId, tlt.addedAt, thing)
    })

    sortMode
      .map(sm => {
        q.sortBy {
          case (_, addedAt, thing) =>
            makeSort(thing, addedAt, sm)
        }
      })
      .getOrElse(q)
  }

  private def makeSort(
    thing: things.ThingsTableRaw,
    addedAt: Rep[OffsetDateTime],
    sortMode: SortMode
  ): ColumnOrdered[
    _ >: Option[Double] with Option[String] with OffsetDateTime <: Any
  ] = {
    sortMode match {
      case Popularity(true)  => thing.popularity.desc.nullsLast
      case Popularity(false) => thing.popularity.desc.nullsLast
      case Recent(desc) =>
        val movieRelease = thing.metadata
          .+>("themoviedb")
          .+>("movie")
          .+>>("release_date")

        val tvRelease = thing.metadata
          .+>("themoviedb")
          .+>("show")
          .+>>("first_air_date")

        val either = movieRelease.ifNull(tvRelease)
        if (desc) {
          either.desc.nullsLast
        } else {
          either.asc.nullsLast
        }

      case AddedTime(true) =>
        addedAt.desc
      case AddedTime(false) =>
        addedAt.asc
      case d @ DefaultForListType(_) => makeSort(thing, addedAt, d.get(true))
    }
  }

  private def countThingsForStandardListQuery(
    listsQuery: ListsQuery,
    thingTypeFilter: Option[Set[ThingType]]
  ) = {
    val thingQuery = InhibitFilter(things.rawQuery)
      .filter(thingTypeFilter)(types => _.`type` inSetBind types)
      .query

    (for {
      lists <- listsQuery
      tlt <- trackedListThings.query if lists.id === tlt.listId
      thing <- thingQuery if thing.id === tlt.thingId
    } yield {
      tlt.listId -> thing
    }).distinctOn(_._2.id)
      .groupBy(_._1)
      .map {
        case (listId, group) => listId -> group.length
      }
  }

  private def makeThingsForListQuery(
    listId: Int,
    thingTypeFilter: Option[Set[ThingType]],
    sortMode: Option[SortMode]
  ) = {
    val thingQuery = InhibitFilter(things.rawQuery)
      .filter(thingTypeFilter)(types => _.`type` inSetBind types)
      .query

    val q = (for {
      tlt <- trackedListThings.query.filter(_.listId === listId)
      thing <- thingQuery if thing.id === tlt.thingId
    } yield {
      (tlt.listId, tlt.addedAt, thing)
    })

    sortMode
      .map(sm => {
        q.sortBy {
          case (_, addedAt, thing) =>
            makeSort(thing, addedAt, sm)
        }
      })
      .getOrElse(q)
  }

  private def makeUserThingTagQuery(
    userId: String,
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

object ListQueryResult {
  val empty = ListQueryResult(None, None)
}
case class ListQueryResult(
  trackedListRow: Option[TrackedList],
  bookmark: Option[Bookmark])