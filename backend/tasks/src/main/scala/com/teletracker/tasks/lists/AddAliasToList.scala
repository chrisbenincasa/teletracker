package com.teletracker.tasks.lists

import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.util.Futures._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID

@JsonCodec
case class AddAliasToListArgs(
  listId: UUID,
  alias: String,
  userId: String)

class AddAliasToList @Inject()(listsDbAccess: ListsDbAccess)
    extends TypedTeletrackerTask[AddAliasToListArgs] {
  override def preparseArgs(args: RawArgs): AddAliasToListArgs =
    AddAliasToListArgs(
      listId = args.valueOrThrow[UUID]("listId"),
      alias = args.valueOrThrow[String]("alias"),
      userId = args.valueOrThrow[String]("userId")
    )

  override protected def runInternal(): Unit = {
    listsDbAccess
      .addAliasToList(args.listId, args.userId, args.alias)
      .await()
  }
}
