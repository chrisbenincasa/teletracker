package com.teletracker.tasks.lists

import com.teletracker.common.elasticsearch.ItemUpdater
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.util.Futures._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID

@JsonCodec
case class AddItemToListArgs(
  listId: UUID,
  itemId: UUID,
  userId: String)

class AddItemToList @Inject()(itemUpdater: ItemUpdater)
    extends TypedTeletrackerTask[AddItemToListArgs] {
  override def preparseArgs(args: RawArgs): AddItemToListArgs =
    AddItemToListArgs(
      listId = args.valueOrThrow[UUID]("listId"),
      itemId = args.valueOrThrow[UUID]("itemId"),
      userId = args.valueOrThrow[String]("userId")
    )

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
