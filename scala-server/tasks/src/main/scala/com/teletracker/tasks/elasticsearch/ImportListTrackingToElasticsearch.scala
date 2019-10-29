package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.access.UsersDbAccess
import com.teletracker.common.db.model.{TrackedListThing, UserThingTagType}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsItem,
  EsItemTag,
  EsUserDenormalizedItem,
  EsUserItem,
  EsUserItemTag
}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import io.circe.parser._
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ImportListTrackingToElasticsearch @Inject()(
  usersDbAccess: UsersDbAccess,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val listById =
      usersDbAccess.getAllLists().await().map(list => list.id -> list).toMap

    usersDbAccess
      .loopThroughAllListTracking() { batch =>
        val newDocById = batch
          .groupBy(_.thingId)
          .toList
          .flatMap {
            case (thingId, tags) =>
              val getRequest = new GetRequest("items", thingId.toString)

              elasticsearchExecutor
                .get(getRequest)
                .flatMap(response => {
                  decode[EsItem](response.getSourceAsString) match {
                    case Left(value) => Future.successful(None)
                    case Right(value) =>
                      val newTags =
                        tags
                          .map(
                            tag =>
                              EsItemTag.userScoped(
                                listById(tag.listId).userId,
                                UserThingTagType.TrackedInList,
                                Some(tag.listId.toDouble),
                                Some(tag.addedAt.toInstant)
                              )
                          )
                          .distinct

                      val uniqueNewTags = newTags.map(_.tag).toSet

                      val finalTags = value.tags
                        .getOrElse(Nil)
                        .filterNot(tag => uniqueNewTags.contains(tag.tag)) ++ newTags.toList

                      val newDoc = value.copy(tags = Some(finalTags))

                      updateItemsIndex(value.id, newDoc).map(_ => {
                        Some(value.id -> newDoc)
                      })
                  }
                })
                .await()
          }
          .toMap

        batch.groupBy(tlt => listById(tlt.listId).userId).foreach {
          case (userId, tags) =>
            updateInvertedIndex(userId, tags, newDocById)
        }

        Future.unit
      }
      .await()
  }

  private def updateItemsIndex(
    id: UUID,
    newDoc: EsItem
  ) = {
    val updateReq =
      new UpdateRequest("items", id.toString)
        .doc(newDoc.asJson.noSpaces, XContentType.JSON)

    elasticsearchExecutor.update(updateReq).map(_ => {})
  }

  private def updateInvertedIndex(
    userId: String,
    tags: Seq[TrackedListThing],
    itemById: Map[UUID, EsItem]
  ): Unit = {
    tags.groupBy(_.thingId).foreach {
      case (itemId, tags) =>
        val item = itemById(itemId)
        val getRequest = new GetRequest("user_items", s"${userId}_${itemId}")

        elasticsearchExecutor
          .get(getRequest)
          .flatMap(response => {
            if (response.getSourceAsString == null) {
              val docTags = tags
                .map(
                  tag =>
                    EsUserItemTag(
                      tag = UserThingTagType.TrackedInList.toString,
                      int_value = Some(tag.listId),
                      last_updated = Some(tag.addedAt.toInstant)
                    )
                )
                .distinct

              val doc = EsUserItem(
                id = s"${userId}_${itemId}",
                item_id = Some(itemId),
                user_id = Some(userId),
                tags = docTags.toList,
                item = Some(
                  EsUserDenormalizedItem(
                    id = item.id,
                    genres = item.genres,
                    release_date = item.release_date,
                    original_title = item.original_title,
                    popularity = item.popularity,
                    slug = item.slug,
                    `type` = item.`type`
                  )
                )
              )

              val indexReq =
                new IndexRequest("user_items")
                  .id(s"${userId}_${itemId}")
                  .create(true)
                  .source(doc.asJson.noSpaces, XContentType.JSON)

              elasticsearchExecutor.index(indexReq).map(_ => {})
            } else {
              decode[EsUserItem](response.getSourceAsString) match {
                case Left(ex) =>
                  println(ex)
                  Future.unit
                case Right(value) =>
                  val newTags =
                    tags
                      .map(
                        tag =>
                          EsUserItemTag(
                            tag = UserThingTagType.TrackedInList.toString,
                            int_value = Some(tag.listId),
                            last_updated = Some(tag.addedAt.toInstant)
                          )
                      )
                      .distinct

                  val uniqueNewTags = newTags.map(_.tag).toSet

                  val finalTags = value.tags
                    .filterNot(tag => uniqueNewTags.contains(tag.tag)) ++ newTags.toList

                  val newDoc = value.copy(
                    item_id = Some(itemId),
                    user_id = Some(userId),
                    tags = finalTags,
                    item = Some(
                      EsUserDenormalizedItem(
                        id = item.id,
                        genres = item.genres,
                        release_date = item.release_date,
                        original_title = item.original_title,
                        popularity = item.popularity,
                        slug = item.slug,
                        `type` = item.`type`
                      )
                    )
                  )

                  val updateReq =
                    new UpdateRequest("user_items", s"${userId}_${itemId}")
                      .doc(newDoc.asJson.noSpaces, XContentType.JSON)

                  elasticsearchExecutor.update(updateReq).map(_ => {})
              }
            }
          })
          .await()
    }
  }
}
