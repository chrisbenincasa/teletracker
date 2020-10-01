package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.ItemUpdater.UpdateItemResult
import com.teletracker.common.elasticsearch.PersonUpdater.UpdatePersonResult
import com.teletracker.common.elasticsearch.async.EsIngestQueue
import com.teletracker.common.elasticsearch.async.EsIngestQueue.{
  AsyncItemUpdateRequest,
  AsyncPersonUpdateRequest
}
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{EsExternalId, EsPerson}
import com.teletracker.common.pubsub.{
  EsIngestItemDenormArgs,
  EsIngestPersonDenormArgs
}
import com.teletracker.common.util.Functions._
import io.circe.Json
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.json.JsonXContent
import org.elasticsearch.common.xcontent._
import org.elasticsearch.script.Script
import org.slf4j.LoggerFactory
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PersonUpdater @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig,
  idMappingLookup: ElasticsearchExternalIdMappingStore,
  updateQueue: EsIngestQueue
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  private val logger = LoggerFactory.getLogger(getClass)

  def insert(person: EsPerson): Future[IndexResponse] = {
    val updateMappingFut = updateMappings(person)
    val indexRequest = getIndexRequest(person)
    val indexFut = elasticsearchExecutor.index(indexRequest)

    for {
      _ <- updateMappingFut
      result <- indexFut
    } yield result
  }

  def update(
    person: EsPerson,
    refresh: Boolean = false
  ): Future[UpdateResponse] = {
    val updateMappingFut = updateMappings(person)
    val updateFut =
      elasticsearchExecutor.update(getUpdateRequest(person, refresh))

    for {
      _ <- updateMappingFut
      result <- updateFut
    } yield result
  }

  def updateFromJson(
    id: UUID,
    json: Json,
    refresh: Boolean = false,
    async: Boolean,
    denormArgs: Option[EsIngestPersonDenormArgs]
  ): Future[UpdatePersonResult] = {
    if (async) {
      updateQueue
        .queuePersonUpdate(
          id = id,
          doc = json,
          denorm = denormArgs
        )
        .map {
          case Some(_) =>
            UpdatePersonResult.queued
          case None =>
            throw new RuntimeException(s"Could not queue update for ${id}")
        }
    } else {
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
        ids <- externalIds
      } yield {
        updateMappings(id, ids)
      }

      val updateFut =
        elasticsearchExecutor.update(getUpdateRequest(id, json, refresh))

      for {
        _ <- mappingsFut.getOrElse(Future.unit)
        updateResp <- updateFut
        _ <- denormArgs
          .filter(_.needsDenorm)
          .map(_ => updateQueue.queuePersonDenormalization(id))
          .getOrElse(Future.unit)
      } yield {
        UpdatePersonResult.syncSuccess(updateResp)
      }
    }
  }

  def updateWithScript(
    id: UUID,
    script: Json,
    refresh: Boolean = false
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
      .applyIf(refresh)(_.setRefreshPolicy(RefreshPolicy.IMMEDIATE))

    elasticsearchExecutor.update(request)
  }

  def getIndexJson(person: EsPerson): String = {
    person.asJson.noSpaces
  }

  def getUpdateJson(
    person: EsPerson,
    refresh: Boolean = false
  ): String = {
    XContentHelper
      .toXContent(
        getUpdateRequest(person, refresh),
        XContentType.JSON,
        ToXContent.EMPTY_PARAMS,
        true
      )
      .utf8ToString
  }

  private def getIndexRequest(person: EsPerson) = {
    new IndexRequest(teletrackerConfig.elasticsearch.people_index_name)
      .create(true)
      .id(person.id.toString)
      .source(person.asJson.noSpaces, XContentType.JSON)
  }

  private def getUpdateRequest(
    person: EsPerson,
    refresh: Boolean
  ): UpdateRequest = {
    getUpdateRequest(person.id, person.asJson, refresh)
  }

  private def getUpdateRequest(
    id: UUID,
    json: Json,
    refresh: Boolean
  ): UpdateRequest = {
    new UpdateRequest(
      teletrackerConfig.elasticsearch.people_index_name,
      id.toString
    ).doc(
        json.deepDropNullValues.noSpaces,
        XContentType.JSON
      )
      .applyIf(refresh)(_.setRefreshPolicy(RefreshPolicy.IMMEDIATE))
  }

  private def updateMappings(item: EsPerson): Future[Unit] = {
    item.external_ids
      .filter(_.nonEmpty)
      .map(externalIds => {
        updateMappings(item.id, externalIds)
      })
      .getOrElse(Future.unit)
  }

  private def updateMappings(
    id: UUID,
    externalIds: List[EsExternalId]
  ): Future[Unit] = {
    val mappings = externalIds
      .filter(isValidExternalIdForMapping)
      .map(externalId => {
        (externalId, ItemType.Person) -> id
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

object PersonUpdater {
  sealed trait UpdatePersonResultStatus
  case class SuccessResult(updateResponse: UpdateResponse)
      extends UpdatePersonResultStatus
  case object QueuedResult extends UpdatePersonResultStatus

  object UpdatePersonResult {
    def syncSuccess(updateResponse: UpdateResponse): UpdatePersonResult =
      UpdatePersonResult(SuccessResult(updateResponse))
    def queued: UpdatePersonResult = UpdatePersonResult(QueuedResult)
  }
  case class UpdatePersonResult(status: UpdatePersonResultStatus)
}
