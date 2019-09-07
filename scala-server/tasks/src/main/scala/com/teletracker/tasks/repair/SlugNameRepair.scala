package com.teletracker.tasks.repair

import com.teletracker.common.db.model.Things
import com.teletracker.common.inject.{DbImplicits, DbProvider}
import com.teletracker.common.util
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Slug
import com.teletracker.common.util.execution.SequentialFutures
import java.util.regex.Pattern
import scala.concurrent.{ExecutionContext, Future}

class SlugNameRepair @Inject()(
  dbProvider: DbProvider,
  dbImplicits: DbImplicits,
  things: Things
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override def run(args: Args): Unit = {
    import dbProvider.driver.api._
    import dbImplicits._

    val brokenSlugs = dbProvider.getDB
      .run {
        things.rawQuery
          .filter(_.normalizedName.asColumnOf[String].like("%--%"))
          .map(t => (t.id, t.name, t.normalizedName))
          .result
      }
      .await()

    val nonLatenChars = Pattern.compile("[^\\w-]")

    SequentialFutures
      .batchedIterator(brokenSlugs.iterator, 32)(batch => {
        val updates = batch.map {
          case (id, name, _ @Slug(_, year)) =>
            val replaced = nonLatenChars.matcher(name).replaceAll("")
            // only treat slugs that are english names, for now...
            if (replaced.nonEmpty) {
              val newSlug = util.Slug(name, year)
              dbProvider.getDB.run {
                things.rawQuery
                  .filter(_.id === id)
                  .map(_.normalizedName)
                  .update(newSlug)
              }
            } else {
              Future.successful(0)
            }
        }

        Future
          .sequence(updates)
          .map(ups => {
            println(s"updated ${ups.sum} rows")
          })
      })
      .await()
  }
}
