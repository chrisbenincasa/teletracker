package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.util.Futures._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID

@JsonCodec
case class CreatePublicListArgs(
  name: String,
  isPublic: Boolean,
  isDynamic: Boolean,
  id: Option[UUID])

class CreatePublicList @Inject()(listsDbAccess: ListsDbAccess)
    extends TypedTeletrackerTask[CreatePublicListArgs] {
  override def preparseArgs(args: RawArgs): CreatePublicListArgs =
    CreatePublicListArgs(
      name = args.valueOrThrow[String]("name"),
      isPublic = args.valueOrDefault("isPublic", false),
      isDynamic = args.valueOrDefault("isDynamic", false),
      id = args.value[UUID]("id")
    )

  override def validateArgs(args: CreatePublicListArgs): Unit = {
    require(args.name.nonEmpty, "Name cannot be empty")
  }

  override protected def runInternal(): Unit = {
    val id = args.id.getOrElse(UUID.randomUUID())
    val now = OffsetDateTime.now()

    listsDbAccess
      .saveList(
        StoredUserList(
          id = id,
          name = args.name,
          isDefault = false,
          isPublic = args.isPublic,
          userId = StoredUserList.PublicUserId,
          isDynamic = args.isDynamic,
          createdAt = Some(now),
          lastUpdatedAt = Some(now)
        )
      )
      .await()

    val list = listsDbAccess.getList(id).await()

    if (list.isEmpty) {
      logger.error("Could not find newly created list")
    } else {
      logger.info(s"Created new list with id = ${id}")
    }
  }
}
