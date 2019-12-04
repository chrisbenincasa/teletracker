package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.{ListsDbAccess => DynamoListsDbAccess}
import com.teletracker.common.db.dynamo.model.{
  StoredUserList,
  UserListRowOptions
}
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.db.legacy_model.TrackedListRow
import com.teletracker.tasks.util.SourceRetriever
import java.net.URI
import java.util.UUID

class MigrateListsToDynamo @Inject()(
  dynamoListsDbAccess: DynamoListsDbAccess,
  sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val listIdFilter = args.value[Int]("listId")
    val input = args.valueOrThrow[URI]("input")

    sourceRetriever
      .getSource(input)
      .getLines()
      .map(TrackedListRow.fromLine(_))
      .filter(list => listIdFilter.forall(_ == list.id))
      .map(list => {
        StoredUserList(
          id = UUID.randomUUID(),
          name = list.name,
          isDefault = list.isDefault,
          isPublic = list.isPublic,
          userId = list.userId,
          isDynamic = list.isDynamic,
          rules = list.rules,
          options =
            list.options.map(o => UserListRowOptions(o.removeWatchedItems)),
          deletedAt = list.deletedAt,
          legacyId = Some(list.id)
        )
      })
      .foreach(list => {
        dynamoListsDbAccess.saveList(list).await()
      })
  }
}
