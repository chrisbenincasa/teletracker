package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.UserThingTagType
import com.teletracker.common.monitoring.Timing
import javax.inject.Inject
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.bulk.{
  BulkItemResponse,
  BulkRequest,
  BulkRequestBuilder
}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.UpdateByQueryRequest
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.script.{Script, ScriptType}
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object ItemUpdater {
  final private val ItemsIndex = "items"
  final private val UserItemsIndex = "user_items"

  // TODO: Store script in ES
  final private val UpdateTagsScriptSource =
    """
      |if (ctx._source.tags == null) {
      |   ctx._source.tags = [params.tag]
      |} else {
      |   ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag));
      |   ctx._source.tags.add(params.tag)
      |}
      |""".stripMargin

  final private val UpdateUserTagsScriptSource =
    """
      |if (ctx._source.tags == null) {
      |   ctx._source.tags = [params.tag]
      |} else {
      |   ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag));
      |   ctx._source.tags.add(params.tag)
      |}
      |""".stripMargin

  final private val RemoveTagsScriptSource =
    """
      |if (ctx._source.tags != null) {
      |   ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag))
      |}
      |""".stripMargin

  final private def UpdateTagsScript(tag: EsItemTag) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      UpdateTagsScriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def UpdateUserTagsScript(tag: EsUserItemTag) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      UpdateUserTagsScriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def RemoveTagScript(tag: EsItemTag) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      RemoveTagsScriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def RemoveUserTagScript(tag: EsUserItemTag) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      RemoveTagsScriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  private def tagAsMap(tag: EsItemTag) = {
    List(
      "tag" -> Some(tag.tag),
      "value" -> tag.value,
      "string_value" -> tag.string_value
    ).collect {
        case (x, Some(v)) => x -> v
      }
      .toMap
      .asJava
  }

  private def tagAsMap(tag: EsUserItemTag) = {
    List(
      "tag" -> Some(tag.tag),
      "int_value" -> tag.int_value,
      "double_value" -> tag.double_value,
      "date_value" -> tag.date_value,
      "string_value" -> tag.string_value,
      "last_updated" -> tag.last_updated
    ).collect {
        case (x, Some(v)) => x -> v
      }
      .toMap
      .asJava
  }
}

class ItemUpdater @Inject()(
  itemSearch: ItemLookup,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext) {
  import ItemUpdater._
  import io.circe.syntax._

  private val logger = LoggerFactory.getLogger(getClass)

  def insert(item: EsItem): Future[IndexResponse] = {
    val indexRequest = new IndexRequest(ItemsIndex)
      .create(true)
      .id(item.id.toString)
      .source(item.asJson.noSpaces, XContentType.JSON)

    elasticsearchExecutor.index(indexRequest)
  }

  def update(item: EsItem): Future[UpdateResponse] = {
    val updateRequest = new UpdateRequest(ItemsIndex, item.id.toString)
      .doc(
        item.asJson.noSpaces,
        XContentType.JSON
      )

    elasticsearchExecutor.update(updateRequest)
  }

  def upsertItemTag(
    itemId: UUID,
    tag: EsItemTag,
    userTag: Option[(String, EsUserItemTag)]
  ): Future[UpdateMultipleDocResponse] = {
    val updateRequest = new UpdateRequest(ItemsIndex, itemId.toString)
      .script(UpdateTagsScript(tag))

    userTag match {
      case Some((userId, value)) =>
        val denormUpdateFut =
          Timing.time("denormUpdateFut", logger) {
            createUserItemTag(itemId, userId, value).flatMap(userItemTag => {
              val updateDenormRequest =
                new UpdateRequest(UserItemsIndex, s"${userId}_${itemId}")
                  .script(UpdateUserTagsScript(value))
                  .upsert(userItemTag.asJson.noSpaces, XContentType.JSON)
                  .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

              elasticsearchExecutor.update(updateDenormRequest)
            })
          }

        val itemUpdateFut = Timing.time("itemUpdateFut", logger) {
          elasticsearchExecutor.update(updateRequest)
        }

        val updateFut = for {
          denormUpdate <- denormUpdateFut
          itemUpdate <- itemUpdateFut
        } yield {
          UpdateMultipleDocResponse(
            error = false,
            Seq(denormUpdate, itemUpdate)
          )
        }

        updateFut.recover {
          case NonFatal(e) =>
            logger.error("Encountered error while adding tag", e)
            UpdateMultipleDocResponse(
              error = true,
              Seq()
            )
        }
      case _ =>
        elasticsearchExecutor
          .update(updateRequest)
          .map(response => {
            UpdateMultipleDocResponse(
              response.status().getStatus > 299,
              Seq(response)
            )
          })
    }
  }

  def removeItemTag(
    itemId: UUID,
    tag: EsItemTag,
    userTag: Option[(String, EsUserItemTag)]
  ): Future[UpdateMultipleDocResponse] = {
    val updateRequest =
      new UpdateRequest(ItemsIndex, itemId.toString)
        .script(RemoveTagScript(tag))

    userTag match {
      case Some((userId, value)) =>
        val updateDenormRequest =
          new UpdateRequest("user_items", s"${userId}_${itemId}")
            .script(RemoveUserTagScript(value))

        val bulkRequest = new BulkRequest()
          .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

        bulkRequest.add(updateDenormRequest)
        bulkRequest.add(updateRequest)

        elasticsearchExecutor
          .bulk(bulkRequest)
          .map(response => {
            UpdateMultipleDocResponse(
              response.hasFailures,
              response.getItems.map(_.getResponse[UpdateResponse])
            )
          })
      case _ =>
        elasticsearchExecutor
          .update(updateRequest)
          .map(response => {
            UpdateMultipleDocResponse(
              response.status().getStatus > 299,
              Seq(response)
            )
          })
    }
  }

  private def createUserItemTag(
    itemId: UUID,
    userId: String,
    userTag: EsUserItemTag
  ) = {
    Timing.time("createUserItemTag", logger) {
      itemSearch
        .lookupItem(Left(itemId), None, materializeJoins = false)
        .map {
          case None =>
            throw new IllegalArgumentException(
              s"Could not find item with id = ${itemId}"
            )
          case Some(item) =>
            makeEsUserItem(itemId, userId, userTag, item.rawItem)
        }
    }
  }

  private def createAndIndexUserItemTag(
    itemId: UUID,
    userId: String,
    userTag: EsUserItemTag
  ): Future[IndexResponse] = {
    createUserItemTag(itemId, userId, userTag).flatMap(userItem => {
      elasticsearchExecutor.index(
        new IndexRequest("user_items")
          .id(userItem.id)
          .source(userItem.asJson.noSpaces, XContentType.JSON)
      )
    })
  }

  private def makeEsUserItem(
    itemId: UUID,
    userId: String,
    userTag: EsUserItemTag,
    item: EsItem
  ) = {
    EsUserItem(
      id = s"${userId}_${itemId}",
      item_id = Some(itemId),
      user_id = Some(userId),
      tags = List(userTag),
      item = Some(item.toDenormalizedUserItem)
    )
  }

  def addListTagToItem(
    itemId: UUID,
    listId: UUID,
    userId: String
  ): Future[UpdateMultipleDocResponse] = {
    val tag =
      EsItemTag.userScopedString(
        userId,
        UserThingTagType.TrackedInList,
        Some(listId.toString),
        Some(Instant.now())
      )

    val userTag = EsUserItemTag.forString(
      tag = UserThingTagType.TrackedInList,
      value = listId.toString
    )

    upsertItemTag(itemId, tag, Some(userId -> userTag))
  }

  def removeListTagFromItem(
    itemId: UUID,
    listId: UUID,
    userId: String
  ): Future[UpdateMultipleDocResponse] = {
    val tag =
      EsItemTag.userScopedString(
        userId,
        UserThingTagType.TrackedInList,
        Some(listId.toString),
        Some(Instant.now())
      )

    val userTag =
      EsUserItemTag.forString(UserThingTagType.TrackedInList, listId.toString)

    removeItemTag(itemId, tag, Some(userId -> userTag))
  }

  // Might be better to fire and forget...
  // TODO: Send request to user_things index too.
  def removeItemFromLists(
    listIds: Set[Int],
    userId: String
  ): Future[Unit] = {
    val responses = listIds.toList.map(listId => {
      val tag =
        EsItemTag
          .userScoped(
            userId,
            UserThingTagType.TrackedInList,
            Some(listId),
            Some(Instant.now())
          )

      val query = QueryBuilders
        .boolQuery()
        .filter(
          QueryBuilders.termQuery("tags.tag", tag.tag)
        )
        .filter(QueryBuilders.termQuery("tags.value", listId))

      val updateRequest = new UpdateByQueryRequest()
        .setScript(RemoveTagScript(tag))
        .setQuery(query)

      elasticsearchExecutor
        .updateByQuery(updateRequest)
        .recover {
          case NonFatal(e) =>
            logger.error(
              s"Couldn't remove tracked items from list id = ${listId}",
              e
            )
        }
        .map(_ => {})
    })

    Future.sequence(responses).map(_ => {})
  }
}

case class UpdateMultipleDocResponse(
  error: Boolean,
  responses: Seq[DocWriteResponse])
