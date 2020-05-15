package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ItemType, UserThingTagType}
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItem,
  EsItemTag,
  EsUserItem,
  EsUserItemTag
}
import com.teletracker.common.monitoring.Timing
import io.circe.Json
import javax.inject.Inject
import org.elasticsearch.action.DocWriteResponse
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.{DeleteRequest, DeleteResponse}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.json.{JsonXContent, JsonXContentParser}
import org.elasticsearch.common.xcontent.{
  DeprecationHandler,
  NamedXContentRegistry,
  ToXContent,
  XContentHelper,
  XContentParser,
  XContentType
}
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

  final private def UpdateTagsScriptSourceWithValueCheck(
    field: Option[String]
  ) = {
    val fieldClause = field match {
      case Some(value) => s" && tag.$value.equals(params.tag.$value)"
      case None        => ""
    }

    val removeClause =
      s"ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag)$fieldClause)"

    s"""
       |if (ctx._source.tags == null) {
       |   ctx._source.tags = [params.tag];
       |} else {
       |  $removeClause;
       |   ctx._source.tags.add(params.tag);
       |}
       |""".stripMargin
  }

  final private def RemoveTagsScriptSourceWithValueCheck(
    field: Option[String]
  ) = {
    val fieldClause = field match {
      case Some(value) => s" && tag.$value.equals(params.tag.$value)"
      case None        => ""
    }

    val removeClause =
      s"ctx._source.tags.removeIf(tag -> tag.tag.equals(params.tag.tag)$fieldClause);"

    s"""
       |if (ctx._source.tags != null) {
       |  $removeClause
       |}
       |""".stripMargin
  }

  final private val UpdateTagsScriptSource =
    UpdateTagsScriptSourceWithValueCheck(None)

  final private val UpdateTagsWithStringValueScriptSource =
    UpdateTagsScriptSourceWithValueCheck(Some("string_value"))

  final private val UpdateTagsWithDoubleValueScriptSource =
    UpdateTagsScriptSourceWithValueCheck(Some("value"))

  final private val UpdateUserTagsScriptSource =
    UpdateTagsScriptSourceWithValueCheck(None)

  final private val UpdateUserTagsWithStringValueScriptSource =
    UpdateTagsScriptSourceWithValueCheck(Some("string_value"))

  final private val UpdateUserTagsWithDoubleValueScriptSource =
    UpdateTagsScriptSourceWithValueCheck(Some("double_value"))

  final private val RemoveTagsScriptSource =
    RemoveTagsScriptSourceWithValueCheck(None)

  final private val RemoveTagsWithStringValueScriptSource =
    RemoveTagsScriptSourceWithValueCheck(Some("string_value"))

  final private val RemoveTagsWithDoubleValueScriptSource =
    RemoveTagsScriptSourceWithValueCheck(Some("value"))

  final private val RemoveUserTagsWithStringValueScriptSource =
    RemoveTagsScriptSourceWithValueCheck(Some("string_value"))

  final private val RemoveUserTagsWithDoubleValueScriptSource =
    RemoveTagsScriptSourceWithValueCheck(Some("double_value"))

  final private def UpdateTagsScript(
    tag: EsItemTag,
    scriptSource: String
  ): Script = {
    new Script(
      ScriptType.INLINE,
      "painless",
      scriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def UpdateUserTagsScript(
    tag: EsUserItemTag,
    scriptSource: String
  ) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      scriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def RemoveTagScript(
    tag: EsItemTag,
    scriptSource: String
  ) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      scriptSource,
      Map[String, Object]("tag" -> tagAsMap(tag)).asJava
    )
  }

  final private def RemoveUserTagScript(
    tag: EsUserItemTag,
    scriptSource: String
  ) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      scriptSource,
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
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig,
  idMappingLookup: ElasticsearchExternalIdMappingStore
)(implicit executionContext: ExecutionContext) {
  import ItemUpdater._
  import io.circe.syntax._

  private val logger = LoggerFactory.getLogger(getClass)

  def insert(item: EsItem): Future[IndexResponse] = {
    val mappingFut = updateMappings(item)

    val indexRequest =
      new IndexRequest(teletrackerConfig.elasticsearch.items_index_name)
        .create(true)
        .id(item.id.toString)
        .source(item.asJson.noSpaces, XContentType.JSON)

    val indexFut = elasticsearchExecutor.index(indexRequest)

    for {
      _ <- mappingFut
      indexResponse <- indexFut
    } yield indexResponse
  }

  def getInsertJson(item: EsItem): String = {
    item.asJson.noSpaces
  }

  def update(item: EsItem): Future[UpdateResponse] = {
    val mappingFut = updateMappings(item)
    val updateFut = elasticsearchExecutor.update(getUpdateRequest(item))

    for {
      _ <- mappingFut
      updateResp <- updateFut
    } yield updateResp
  }

  def updateFromJson(
    id: UUID,
    itemType: Option[ItemType],
    json: Json
  ): Future[UpdateResponse] = {
    val externalIds = json.asObject
      .flatMap(
        obj =>
          obj("external_ids")
            .flatMap(_.asArray)
            .map(_.toList.flatMap(_.asString))
            .map(_.map(EsExternalId.parse))
      )
      .filter(_.nonEmpty)

    val mappingsFut = for {
      typ <- itemType
      ids <- externalIds
    } yield {
      updateMappings(id, typ, ids)
    }

    val updateFut = elasticsearchExecutor.update(getUpdateRequest(id, json))

    for {
      _ <- mappingsFut.getOrElse(Future.unit)
      updateResp <- updateFut
    } yield updateResp
  }

  def updateWithScript(
    id: UUID,
    script: Json
  ): Future[UpdateResponse] = {
    val request = new UpdateRequest(
      teletrackerConfig.elasticsearch.items_index_name,
      id.toString
    ).script(
      Script.parse(
        JsonXContent.jsonXContent.createParser(
          NamedXContentRegistry.EMPTY,
          DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
          script.deepDropNullValues.noSpaces
        )
      )
    )

    elasticsearchExecutor.update(request)
  }

  def getUpdateJson(
    item: EsItem,
    pretty: Boolean = true
  ): String = {
    XContentHelper
      .toXContent(
        getUpdateRequest(item),
        XContentType.JSON,
        ToXContent.EMPTY_PARAMS,
        pretty
      )
      .utf8ToString
  }

  private def getUpdateRequest(item: EsItem): UpdateRequest = {
    getUpdateRequest(item.id, item.asJson)
  }

  private def getUpdateRequest(
    id: UUID,
    json: Json
  ): UpdateRequest = {
    new UpdateRequest(
      teletrackerConfig.elasticsearch.items_index_name,
      id.toString
    ).doc(
      json.deepDropNullValues.noSpaces,
      XContentType.JSON
    )
  }

  def delete(id: UUID): Future[DeleteResponse] = {
    val deleteRequest = new DeleteRequest(
      teletrackerConfig.elasticsearch.items_index_name,
      id.toString
    )

    elasticsearchExecutor.delete(deleteRequest)
  }

  def upsertItemTag(
    itemId: UUID,
    tag: EsItemTag,
    userTag: Option[(String, EsUserItemTag)]
  ): Future[UpdateMultipleDocResponse] = {
    val scriptSource = tag match {
      case EsItemTag(_, Some(_), _, _) => UpdateTagsWithDoubleValueScriptSource
      case EsItemTag(_, _, Some(_), _) => UpdateTagsWithStringValueScriptSource
      case EsItemTag(_, None, None, _) => UpdateTagsScriptSource
      case _ =>
        throw new IllegalArgumentException(s"Unexpected tag on update: $tag")
    }

    val updateRequest = new UpdateRequest(
      teletrackerConfig.elasticsearch.items_index_name,
      itemId.toString
    ).script(UpdateTagsScript(tag, scriptSource))

    userTag match {
      case Some((userId, value)) =>
        val userTagsScriptSource = value match {
          case EsUserItemTag(_, _, _, _, Some(_), _, _, _) =>
            UpdateUserTagsWithDoubleValueScriptSource
          case EsUserItemTag(_, _, _, _, _, _, Some(_), _) =>
            UpdateUserTagsWithStringValueScriptSource
          case EsUserItemTag(_, _, _, None, None, None, None, _) =>
            UpdateUserTagsScriptSource
          case _ =>
            throw new IllegalArgumentException(
              s"Unexpected user_item tag on remove: $value"
            )
        }

        val denormUpdateFut =
          Timing.time("denormUpdateFut", logger) {
            createUserItemTag(itemId, userId, value).flatMap(userItemTag => {
              val updateDenormRequest =
                new UpdateRequest(
                  teletrackerConfig.elasticsearch.user_items_index_name,
                  s"${userId}_${itemId}"
                ).script(UpdateUserTagsScript(value, userTagsScriptSource))
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
    val scriptSource = tag match {
      case EsItemTag(_, Some(_), _, _) => RemoveTagsWithDoubleValueScriptSource
      case EsItemTag(_, _, Some(_), _) => RemoveTagsWithStringValueScriptSource
      case EsItemTag(_, None, None, _) => RemoveTagsScriptSource
      case _ =>
        throw new IllegalArgumentException(s"Unexpected tag on remove: $tag")
    }

    val updateRequest =
      new UpdateRequest(
        teletrackerConfig.elasticsearch.items_index_name,
        itemId.toString
      ).script(RemoveTagScript(tag, scriptSource))

    userTag match {
      case Some((userId, value)) =>
        val userTagsScriptSource = value match {
          case EsUserItemTag(_, _, _, _, Some(_), _, _, _) =>
            RemoveUserTagsWithDoubleValueScriptSource
          case EsUserItemTag(_, _, _, _, _, _, Some(_), _) =>
            RemoveUserTagsWithStringValueScriptSource
          case EsUserItemTag(_, _, _, None, None, None, None, _) =>
            RemoveTagsScriptSource
          case _ =>
            throw new IllegalArgumentException(
              s"Unexpected user_item tag on remove: $value"
            )
        }

        val updateDenormRequest =
          new UpdateRequest("user_items", s"${userId}_${itemId}")
            .script(RemoveUserTagScript(value, userTagsScriptSource))

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
        .lookupItem(
          Left(itemId),
          None,
          shouldMateralizeCredits = false,
          shouldMaterializeRecommendations = false
        )
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
        .setScript(RemoveTagScript(tag, RemoveTagsWithStringValueScriptSource))
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

  private def updateMappings(item: EsItem): Future[Unit] = {
    item.external_ids
      .filter(_.nonEmpty)
      .map(externalIds => {
        updateMappings(item.id, item.`type`, externalIds)
      })
      .getOrElse(Future.unit)
  }

  private def updateMappings(
    id: UUID,
    itemType: ItemType,
    externalIds: List[EsExternalId]
  ): Future[Unit] = {
    val mappings = externalIds
      .filter(isValidExternalIdForMapping)
      .map(externalId => {
        (externalId, itemType) -> id
      })

    idMappingLookup.mapExternalIds(mappings.toMap).recover {
      case NonFatal(e) =>
        logger.warn(
          s"Error while attempting to map external Ids for id = ${id}",
          e
        )
    }
  }

  private def isValidExternalIdForMapping(externalId: EsExternalId) = {
    externalId.id.nonEmpty && externalId.id != "0"
  }
}

case class UpdateMultipleDocResponse(
  error: Boolean,
  responses: Seq[DocWriteResponse])
