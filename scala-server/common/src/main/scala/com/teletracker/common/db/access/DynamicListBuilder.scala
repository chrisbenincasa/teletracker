package com.teletracker.common.db.access

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
import javax.inject.Inject
import slick.lifted.ColumnOrdered
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DynamicListBuilder @Inject()(
  val provider: BaseDbProvider,
  val userThingTags: UserThingTags,
  val personThing: PersonThings,
  val things: Things,
  val trackedLists: TrackedLists,
  val thingGenres: ThingGenres,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {
  import dbImplicits._
  import provider.driver.api._

  def countMatchingThings(
    listIds: Set[Int]
  )(implicit executionContext: ExecutionContext
  ) = {
    trackedLists.query
      .filter(_.id inSetBind listIds)
      .filter(_.isDynamic)
      .filter(_.deletedAt.isEmpty)
      .filter(_.rules.isDefined)
      .result
      .flatMap(lists => {

        val i = lists.map(list => {
          buildBase(list, list.userId)
            .map(_._1._1.id)
            .distinct
            .map(list.id -> _)
            .groupBy(_._1)
            .map {
              case (listId, group) => listId -> group.length
            }
            .result
        })

        DBIO.sequence(i).map(_.flatten)
      })
  }

  def buildList(
    userId: String,
    dynamicList: TrackedListRow,
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    includeActions: Boolean = true,
    limit: Int = 20
  )(implicit executionContext: ExecutionContext
  ) = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    if (bookmark.isDefined) {
      bookmark.get.sortMode match {
        case AddedTime(_) =>
          throw new IllegalArgumentException(
            "Invalid bookmark for dynamic list. Cannot sort by added time on a dynamic list."
          )
        case _ =>
      }
    }

    val sortToUse = if (bookmark.isDefined) {
      bookmark.get.sortMode
    } else {
      sortMode
    }

    val rules = dynamicList.rules.get

    if (rules.rules.nonEmpty) {
      val query1 = buildBase(dynamicList, userId, bookmark)
        .map(_._1._1)
        .sortBy(makeSort(_, sortToUse))
        .map(_.id)
        .take(limit)
        .result

      val genresQuery = query1
        .flatMap(thingIds => {
          thingGenres.query
            .filter(_.thingId inSetBind thingIds)
            .map(genre => {
              genre.thingId -> genre.genreId
            })
            .result
        })

      val thingsQuery = query1
        .flatMap(thingIds => {
          things.rawQuery
            .filter(_.id inSetBind thingIds.toSet)
            .joinLeft(userThingTags.query)
            .on(_.id === _.thingId)
            .sortBy {
              case (thing, _) =>
                makeSort(thing, sortMode)
            }
            .result
            .map(thingsAndActions => {
              val (things, actionOpts) = thingsAndActions.unzip
              val thingIds = things.map(_.id).distinct
              val thingsById = things.groupBy(_.id).mapValues(_.head)
              val actions = actionOpts.flatten.groupBy(_.thingId)

              thingIds.map(id => {
                thingsById(id) -> actions.getOrElse(id, Seq.empty)
              })
            })
        })

      val thingsFut = run(thingsQuery)
      val genreFut = run(genresQuery)

      for {
        things <- thingsFut
        genres <- genreFut
      } yield {
        val genresByThingId = genres.groupBy(_._1).mapValues(_.map(_._2).toSet)
        things.map {
          case (thing, actions) =>
            (thing, actions, genresByThingId.getOrElse(thing.id, Set.empty))
        }
      }
    } else {
      Future.successful(Seq.empty)
    }
  }

  private def buildBase(
    dynamicList: TrackedListRow,
    userId: String,
    bookmark: Option[Bookmark]
  ) = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    require(rules.rules.nonEmpty)

    val tagRules = rules.rules.collect {
      case tagRule: DynamicListTagRule => tagRule
    }

    val personRules = rules.rules.collect {
      case personRule: DynamicListPersonRule => personRule
    }

    val tagsQ = userThingTags.query.filter {
      case tag if tagRules.nonEmpty =>
        val base = tag.userId === userId
        if (tagRules.nonEmpty) {
          base && tagRules
            .map(tag.action === _.tagType)
            .reduceLeft(_ && _)
        } else base
      case _ =>
        LiteralColumn(true).c
    }

    val withTagRulesQuery = if (tagRules.nonEmpty) {
      things.rawQuery
        .join(tagsQ)
        .on {
          case (thing, tag) =>
            thing.id === tag.thingId
        }
        .filter {
          case (_, tag) =>
            val base = tag.userId === userId
            if (tagRules.nonEmpty) {
              base && tagRules
                .map(tag.action === _.tagType)
                .reduceLeft(_ && _)
            } else base
        }
    } else {
      things.rawQuery.joinLeft(tagsQ).on {
        case (thing, tag) =>
          thing.id === tag.thingId
      }
    }

    val withPersonQuery = withTagRulesQuery
      .joinLeft(personThing.query)
      .on {
        case ((thing, _), xo) if personRules.nonEmpty =>
          thing.id === xo.thingId
        case (_, _) => LiteralColumn(false)
      }
      .filter {
        case ((_, _), z) if personRules.nonEmpty =>
          personRules
            .map(z.map(_.personId) === _.personId)
            .reduceLeft(_ || _)
        case _ =>
          LiteralColumn(Option(true)).c
      }

    InhibitFilter(withPersonQuery)
      .filter(bookmark)(b => {
        case ((thing, _), _) =>
          b.sortMode match {
            case Popularity(desc) =>
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
                either < b.value
              } else {
                either > b.value
              }

            case AddedTime(_)             =>
            case DefaultForListType(desc) =>
          }
      })
      .query
  }

  private def applyBookmarkFilter(
    thing: things.ThingsTableRaw,
    bookmark: Bookmark
  ) = {

    @scala.annotation.tailrec
    def applyForSortMode(sortMode: SortMode) = {
      sortMode match {
        case Popularity(desc) =>
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
            either < bookmark.value
          } else {
            either > bookmark.value
          }

        case AddedTime(desc)           => applyForSortMode(Recent(desc))
        case d @ DefaultForListType(_) => applyForSortMode(d.get(true))
      }
    }

    applyForSortMode(bookmark.sortMode)
  }

  @scala.annotation.tailrec
  private def makeSort(
    thing: things.ThingsTableRaw,
    sortMode: SortMode
  ): (
    ColumnOrdered[_ >: Option[Double] with Option[String] <: Option[Any]],
    ColumnOrdered[UUID]
  ) = {
    sortMode match {
      case Popularity(true)  => (thing.popularity.desc.nullsLast, thing.id.asc)
      case Popularity(false) => (thing.popularity.desc.nullsLast, thing.id.asc)
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
        val ordered = if (desc) {
          either.desc.nullsLast
        } else {
          either.asc.nullsLast
        }

        ordered -> thing.id.asc

      case AddedTime(desc)           => makeSort(thing, Recent(desc))
      case d @ DefaultForListType(_) => makeSort(thing, d.get(true))
    }
  }
}
