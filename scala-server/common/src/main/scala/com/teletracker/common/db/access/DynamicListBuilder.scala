package com.teletracker.common.db.access

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.db.{
  AddedTime,
  BaseDbProvider,
  DbImplicits,
  DefaultForListType,
  Popularity,
  Recent,
  SortMode,
  SyncDbProvider
}
import com.teletracker.common.db.model._
import com.teletracker.common.util.GeneralizedDbFactory
import javax.inject.Inject
import slick.lifted.ColumnOrdered
import scala.concurrent.ExecutionContext

class DynamicListBuilder @Inject()(
  val baseDbProvider: BaseDbProvider,
  val userThingTags: UserThingTags,
  val personThing: PersonThings,
  val things: Things,
  val trackedLists: TrackedLists,
  dbImplicits: DbImplicits) {
  import dbImplicits._
  import baseDbProvider.driver.api._

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

  private def buildBase(
    dynamicList: TrackedListRow,
    userId: String
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

    withTagRulesQuery
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
  }

  private def makeSort(
    thing: things.ThingsTableRaw,
    sortMode: SortMode
  ): ColumnOrdered[_ >: Option[Double] with Option[String] <: Option[Any]] = {
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

      case AddedTime(desc)           => makeSort(thing, Recent(desc))
      case d @ DefaultForListType(_) => makeSort(thing, d.get(true))
    }
  }

  def buildList(
    userId: String,
    dynamicList: TrackedListRow,
    sortMode: SortMode = Popularity(),
    includeActions: Boolean = true
  )(implicit executionContext: ExecutionContext
  ): DBIOAction[Seq[(ThingRaw, Seq[UserThingTag])], NoStream, Effect.Read] = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    if (rules.rules.nonEmpty) {
      val query1 = buildBase(dynamicList, userId)
        .map(_._1._1)
        .sortBy {
          case thing =>
            makeSort(thing, sortMode)
        }
        .map(_.id)
        .take(20)
        .result

      query1.statements.foreach(println)

      query1
        .flatMap(thingIds => {

          val query2 = things.rawQuery
            .filter(_.id inSetBind thingIds.toSet)
            .joinLeft(userThingTags.query)
            .on(_.id === _.thingId)
            .sortBy {
              case (thing, _) =>
                makeSort(thing, sortMode)
            }
            .result

          query2.statements.foreach(println)

          query2.map(thingsAndActions => {
            val (things, actionOpts) = thingsAndActions.unzip
            val thingIds = things.map(_.id).distinct
            val thingsById = things.groupBy(_.id).mapValues(_.head)
            val actions = actionOpts.flatten.groupBy(_.thingId)

            thingIds.map(id => {
              thingsById(id) -> actions.getOrElse(id, Seq.empty)
            })
          })
        })
    } else {
      DBIO.successful(Seq.empty)
    }
  }
}
