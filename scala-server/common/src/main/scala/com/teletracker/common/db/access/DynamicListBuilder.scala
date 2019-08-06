package com.teletracker.common.db.access

import com.teletracker.common.db.model.{Things, TrackedListRow, UserThingTags}
import com.teletracker.common.inject.{DbImplicits, DbProvider}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DynamicListBuilder @Inject()(
  val provider: DbProvider,
  val userThingTags: UserThingTags,
  val things: Things,
  dbImplicits: DbImplicits) {
  import dbImplicits._
  import provider.driver.api._

  def buildList(
    userId: Int,
    dynamicList: TrackedListRow,
    includeActions: Boolean = true
  )(implicit executionContext: ExecutionContext
  ) = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    if (rules.tagRules.nonEmpty) {
      val q = userThingTags.query.filter(utt => {
        val clause =
          rules.tagRules.foldLeft(LiteralColumn(false): Rep[Boolean]) {
            case (acc, rule) =>
              acc || utt.action === rule.tagType
          }

        utt.userId === userId && clause
      })

      val thingsQuery = q
        .map(_.thingId)
        .distinct join things.rawQuery on (_.value === _.id)

      val finalThingsQuery = thingsQuery joinLeft userThingTags.query.filter(
        _.userId === userId
      ) on (_._2.id === _.thingId)

      finalThingsQuery
        .map {
          case ((_, thing), actions) => thing -> actions
        }
        .result
        .map(thingsAndActions => {
          val (things, actionOpts) = thingsAndActions.unzip
          val actions = actionOpts.flatten.groupBy(_.thingId)
          things.map(thing => {
            thing -> actions.getOrElse(thing.id, Seq.empty)
          })
        })
    } else {
      DBIO.successful(Seq.empty)
    }
  }
}
