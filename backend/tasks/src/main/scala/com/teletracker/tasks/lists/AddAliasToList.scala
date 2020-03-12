package com.teletracker.tasks.lists

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.util.Futures._
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID

case class AddAliasToListArgs(
  listId: UUID,
  alias: String,
  userId: String)

class AddAliasToList @Inject()(listsDbAccess: ListsDbAccess)
    extends TeletrackerTask {
  override type TypedArgs = AddAliasToListArgs

  implicit override protected def typedArgsEncoder
    : Encoder[AddAliasToListArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): AddAliasToListArgs =
    AddAliasToListArgs(
      listId = args.valueOrThrow[UUID]("listId"),
      alias = args.valueOrThrow[String]("alias"),
      userId = args.valueOrThrow[String]("userId")
    )

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)
    listsDbAccess
      .addAliasToList(parsedArgs.listId, parsedArgs.userId, parsedArgs.alias)
      .await()
  }
}
