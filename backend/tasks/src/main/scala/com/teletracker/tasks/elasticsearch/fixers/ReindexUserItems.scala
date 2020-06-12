package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.elasticsearch.model.EsUserItem
import com.teletracker.common.elasticsearch.{ItemLookup, UserItemsScroller}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.concurrent.duration._

class ReindexUserItems @Inject()(
  userItemsScroller: UserItemsScroller,
  teletrackerConfig: TeletrackerConfig,
  itemLookup: ItemLookup,
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(): Unit = {
    val userId = rawArgs.value[String]("userId")

    val allUserItems = userItemsScroller
      .start(
        QueryBuilders.matchAllQuery()
      )
      .toList
      .await()

    val byItemId = allUserItems.groupBy(_.item_id)

    itemLookup
      .lookupItemsByIds(byItemId.keySet)
      .flatMap(found => {
        AsyncStream
          .fromSeq(found.toSeq)
          .collect {
            case (uuid, Some(item)) => uuid -> item
          }
          .flatMapSeq {
            case (uuid, item) =>
              byItemId
                .get(uuid)
                .map(_.map(userItem => {
                  (userItem, item)
                }))
                .toList
                .flatten
                .filter {
                  case (userItem, _) =>
                    userId.forall(_ == userItem.user_id)
                }
          }
          .delayedForeachF(500 millis, scheduler) {
            case (userItem, item) =>
              denormalizedItemUpdater
                .updateUserItem(
                  EsUserItem.create(
                    itemId = item.id,
                    userId = userItem.user_id,
                    tags = userItem.tags,
                    item = Some(item.toDenormalizedUserItem)
                  )
                )
                .recover {
                  case NonFatal(e) =>
                    logger.warn(s"Could not update ${userItem.id}", e)
                }
          }
      })
      .await()
  }
}
