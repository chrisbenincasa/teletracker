package com.teletracker.tasks.lists

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.elasticsearch.ItemUpdater
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID
import com.teletracker.common.util.Futures._

case class AddItemToListArgs(
  listId: UUID,
  itemId: UUID,
  userId: String)

class AddItemToList @Inject()(itemUpdater: ItemUpdater)
    extends TeletrackerTask {
  override type TypedArgs = AddItemToListArgs

  implicit override protected def typedArgsEncoder: Encoder[AddItemToListArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): AddItemToListArgs =
    AddItemToListArgs(
      listId = args.valueOrThrow[UUID]("listId"),
      itemId = args.valueOrThrow[UUID]("itemId"),
      userId = args.valueOrThrow[String]("userId")
    )

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)
    itemUpdater
      .addListTagToItem(
        parsedArgs.itemId,
        parsedArgs.listId,
        parsedArgs.userId
      )
      .await()
  }
}
