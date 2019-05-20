package com.teletracker.service.db

import com.teletracker.service.db.model.{Things, TrackedListRow, UserThingTags}
import com.teletracker.service.inject.{DbImplicits, DbProvider}
import javax.inject.Inject

class DynamicListBuilder @Inject()(
  val provider: DbProvider,
  val userThingTags: UserThingTags,
  val things: Things,
  dbImplicits: DbImplicits
) {
  import dbImplicits._
  import provider.driver.api._

  def buildList(userId: Int, dynamicList: TrackedListRow) = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    if (rules.tagRules.nonEmpty) {
      val q = userThingTags.query.filter(utt => {
        val clause = rules.tagRules.foldLeft(LiteralColumn(false): Rep[Boolean]) {
          case (acc, rule) =>
            acc || utt.action === rule.tagType
        }

        utt.userId === userId && clause
      })

      (q.map(_.thingId).distinct join things.rawQuery on(_.value === _.id)).map {
        case (_, thing) => thing
      }.result
    } else {
      DBIO.successful(Seq.empty)
    }
  }
}
