package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItem,
  EsPerson
}
import com.teletracker.common.util.Functions._
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.{
  ToXContent,
  XContentHelper,
  XContentType
}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PersonUpdater @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig,
  idMappingLookup: ElasticsearchExternalIdMappingStore
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

  def getIndexJson(person: EsPerson) = {
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
  ) = {
    new UpdateRequest(
      teletrackerConfig.elasticsearch.people_index_name,
      person.id.toString
    ).doc(
        person.asJson.noSpaces,
        XContentType.JSON
      )
      .applyIf(refresh)(_.setRefreshPolicy(RefreshPolicy.IMMEDIATE))
  }

  private def updateMappings(item: EsPerson): Future[Unit] = {
    item.external_ids
      .filter(_.nonEmpty)
      .map(externalIds => {
        val mappings = externalIds
          .filter(isValidExternalIdForMapping)
          .map(externalId => {
            (externalId, ItemType.Person) -> item.id
          })

        idMappingLookup.mapExternalIds(mappings.toMap).recover {
          case NonFatal(e) =>
            logger.warn(
              s"Error while attempting to map external Ids for id = ${item.id}",
              e
            )
        }
      })
      .getOrElse(Future.unit)
  }

  private def isValidExternalIdForMapping(externalId: EsExternalId) = {
    externalId.id.nonEmpty && externalId.id != "0"
  }
}
