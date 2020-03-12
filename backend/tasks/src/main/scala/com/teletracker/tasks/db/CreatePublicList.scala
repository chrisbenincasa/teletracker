package com.teletracker.tasks.db

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.util.Futures._
import io.circe.Encoder
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID

case class CreatePublicListArgs(
  name: String,
  isPublic: Boolean,
  isDynamic: Boolean,
  id: Option[UUID])

class CreatePublicList @Inject()(listsDbAccess: ListsDbAccess)
    extends TeletrackerTask {
  override type TypedArgs = CreatePublicListArgs

  implicit override protected def typedArgsEncoder
    : Encoder[CreatePublicListArgs] = io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): CreatePublicListArgs =
    CreatePublicListArgs(
      name = args.valueOrThrow[String]("name"),
      isPublic = args.valueOrDefault("isPublic", false),
      isDynamic = args.valueOrDefault("isDynamic", false),
      id = args.value[UUID]("id")
    )

  override def validateArgs(args: CreatePublicListArgs): Unit = {
    require(args.name.nonEmpty, "Name cannot be empty")
  }

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)

    val id = parsedArgs.id.getOrElse(UUID.randomUUID())
    val now = OffsetDateTime.now()

    listsDbAccess
      .saveList(
        StoredUserList(
          id = id,
          name = parsedArgs.name,
          isDefault = false,
          isPublic = parsedArgs.isPublic,
          userId = "public-user",
          isDynamic = parsedArgs.isDynamic,
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
