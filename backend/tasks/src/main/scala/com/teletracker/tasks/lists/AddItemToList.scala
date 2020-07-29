package com.teletracker.tasks.lists

import com.teletracker.common.elasticsearch.ItemUpdater
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID

@JsonCodec
@GenArgParser
case class AddItemToListArgs(
  listId: UUID,
  itemId: UUID,
  userId: String)

object AddItemToListArgs

class AddItemToList @Inject()(itemUpdater: ItemUpdater)
    extends TypedTeletrackerTask[AddItemToListArgs] {
  override protected def runInternal(): Unit = {
    itemUpdater
      .addListTagToItem(
        args.itemId,
        args.listId,
        args.userId
      )
      .await()
  }
}
