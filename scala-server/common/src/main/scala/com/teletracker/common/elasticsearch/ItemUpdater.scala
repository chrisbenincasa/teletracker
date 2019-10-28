package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.UserThingTagType
import javax.inject.Inject
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.UpdateByQueryRequest
import org.elasticsearch.script.{Script, ScriptType}
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object ItemUpdater {
  // TODO: Store script in ES
  final private val UpdateTagsScriptSource =
    """
      |if (ctx._source.tags == null) {
      |   ctx._source.tags = [params.tag]
      |} else if (ctx._source.tags.find(tag -> tag.tag.equals(params.tag.tag)) == null) { 
      |   ctx._source.tags.add(params.tag) 
      |} else {
      |   ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag))
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

  final private def RemoveTagScript(tag: EsItemTag) = {
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
      "value" -> tag.value
    ).collect {
        case (x, Some(v)) => x -> v
      }
      .toMap
      .asJava
  }
}

class ItemUpdater @Inject()(
  itemSearch: ItemSearch,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext) {
  import ItemUpdater._
  import io.circe.syntax._

  private val logger = LoggerFactory.getLogger(getClass)

  def insert(item: EsItem) = {
    val indexRequest = new IndexRequest("items")
      .create(true)
      .id(item.id.toString)
      .source(item.asJson.noSpaces, XContentType.JSON)

    elasticsearchExecutor.index(indexRequest)
  }

  def update(item: EsItem): Future[UpdateResponse] = {
    val updateRequest = new UpdateRequest("items", item.id.toString)
      .doc(
        item.asJson.noSpaces,
        XContentType.JSON
      )

    elasticsearchExecutor.update(updateRequest)
  }

  def upsertItemTag(
    itemId: UUID,
    tag: EsItemTag
  ): Future[UpdateResponse] = {
    val updateRequest = new UpdateRequest("items", itemId.toString)
      .script(UpdateTagsScript(tag))

    elasticsearchExecutor.update(updateRequest)
  }

  def removeItemTag(
    itemId: UUID,
    tag: EsItemTag
  ): Future[UpdateResponse] = {
    val updateRequest =
      new UpdateRequest("items", itemId.toString).script(RemoveTagScript(tag))

    elasticsearchExecutor.update(updateRequest)
  }

  def addListTagToItem(
    itemId: UUID,
    listId: Int,
    userId: String
  ): Future[UpdateResponse] = {
    val tag =
      EsItemTag.userScoped(
        userId,
        UserThingTagType.TrackedInList,
        Some(listId),
        Some(Instant.now())
      )
    upsertItemTag(itemId, tag)
  }

  def removeListTagFromItem(
    itemId: UUID,
    listId: Int,
    userId: String
  ): Future[UpdateResponse] = {
    val tag =
      EsItemTag.userScoped(
        userId,
        UserThingTagType.TrackedInList,
        Some(listId),
        Some(Instant.now())
      )

    removeItemTag(itemId, tag)
  }

  // Might be better to fire and forget...
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
