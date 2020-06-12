package com.teletracker.common.elasticsearch.lookups

import com.teletracker.common.db.dynamo.DynamoAccess
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.util.AsyncStream
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  BatchGetItemRequest,
  BatchWriteItemRequest,
  DeleteRequest,
  GetItemRequest,
  KeysAndAttributes,
  PutItemRequest,
  PutRequest,
  QueryRequest,
  WriteRequest
}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.util.{Failure, Success, Try}

object DynamoElasticsearchExternalIdMapping {
  final val TableName = "teletracker.qa.id_mapping"
}

class DynamoElasticsearchExternalIdMapping @Inject()(
  dynamo: DynamoDbAsyncClient
)(implicit executionContext: ExecutionContext)
    extends DynamoAccess(dynamo)
    with ElasticsearchExternalIdMappingStore {
  private val logger = LoggerFactory.getLogger(getClass)

  override def getItemIdForExternalId(
    externalId: EsExternalId,
    itemType: ItemType
  ): Future[Option[UUID]] = {
    val request = GetItemRequest
      .builder()
      .key(
        Map(
          "externalId" -> makeExternalIdKey(externalId, itemType)
        ).asJava
      )
      .tableName(DynamoElasticsearchExternalIdMapping.TableName)
      .build()

    dynamo
      .getItem(request)
      .toScala
      .map(response => {
        response.item().asScala.get("id").map(_.valueAs[UUID])
      })
  }

  override def getItemIdsForExternalIds(
    lookupPairs: Set[(EsExternalId, ItemType)]
  ): Future[Map[(EsExternalId, ItemType), UUID]] = {
    if (lookupPairs.isEmpty) {
      Future.successful(Map.empty)
    } else {
      AsyncStream
        .fromSeq(lookupPairs.toSeq)
        .grouped(100)
        .mapF(pairs => {
          val keys = pairs.map {
            case (id, itemType) =>
              Map(
                "externalId" -> makeExternalIdKey(id, itemType)
              ).asJava
          }.asJavaCollection

          val attributes = KeysAndAttributes.builder().keys(keys).build()

          dynamo
            .batchGetItem(
              BatchGetItemRequest
                .builder()
                .requestItems(
                  Map(
                    DynamoElasticsearchExternalIdMapping.TableName -> attributes
                  ).asJava
                )
                .build()
            )
            .toScala
            .map(response => {
              response
                .responses()
                .asScala
                .get(DynamoElasticsearchExternalIdMapping.TableName)
                .map(responses => {
                  responses.asScala
                    .map(_.asScala)
                    .flatMap(row => {
                      for {
                        key <- row
                          .get("externalId")
                          .map(_.valueAs[String])
                          .flatMap(parseKey)
                        value <- row.get("id").map(_.valueAs[UUID])
                      } yield {
                        key -> value
                      }
                    })
                    .toMap
                })
                .getOrElse(Map.empty)
            })
        })
        .foldLeft(Map.empty[(EsExternalId, ItemType), UUID])(_ ++ _)
    }
  }

  override def getExternalIdsForItemId(
    itemId: UUID,
    itemType: ItemType
  ): Future[List[EsExternalId]] = {
    queryLoop(
      DynamoElasticsearchExternalIdMapping.TableName,
      builder =>
        builder
          .tableName(DynamoElasticsearchExternalIdMapping.TableName)
          .indexName("id-to-externals")
          .keyConditionExpression("id = :v1")
          .expressionAttributeValues(
            Map(
              ":v1" -> itemId.toAttributeValue
            ).asJava
          )
    ).map(rows => {
      rows.flatMap(row => {
        row.asScala
          .get("externalId")
          .map(_.valueAs[String])
          .flatMap(externalIdType => {
            parseKey(externalIdType).collect {
              case (id, typ) if typ == itemType => id
            }
          })
      })
    })
  }

  override def mapExternalId(
    externalId: EsExternalId,
    itemType: ItemType,
    id: UUID
  ): Future[Unit] = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .item(
            Map(
              "externalId" -> makeExternalIdKey(externalId, itemType),
              "id" -> id.toAttributeValue
            ).asJava
          )
          .build()
      )
      .toScala
      .map(_ => {})
  }

  override def mapExternalIds(
    mappings: Map[(EsExternalId, ItemType), UUID]
  ): Future[Unit] = {
    if (mappings.isEmpty) {
      Future.unit
    } else {
      AsyncStream
        .fromSeq(mappings.toSeq)
        .grouped(25)
        .mapF(batch => {
          val puts = batch
            .map {
              case ((externalId, itemType), uuid) =>
                PutRequest
                  .builder()
                  .item(
                    Map(
                      "externalId" -> makeExternalIdKey(externalId, itemType),
                      "id" -> uuid.toAttributeValue
                    ).asJava
                  )
                  .build()
            }
            .map(put => WriteRequest.builder().putRequest(put).build())
            .asJavaCollection

          dynamo
            .batchWriteItem(
              BatchWriteItemRequest
                .builder()
                .requestItems(
                  Map(DynamoElasticsearchExternalIdMapping.TableName -> puts).asJava
                )
                .build()
            )
            .toScala
            .map(_ => {})
        })
        .force
    }
  }

  override def unmapExternalIds(
    ids: Set[(EsExternalId, ItemType)]
  ): Future[Unit] = {
    if (ids.isEmpty) {
      Future.unit
    } else {
      AsyncStream
        .fromSeq(ids.toSeq)
        .grouped(25)
        .mapF(
          batch => {
            val deletes = batch
              .map(key => {
                DeleteRequest
                  .builder()
                  .key(
                    Map("externalId" -> makeExternalIdKey(key._1, key._2)).asJava
                  )
                  .build()
              })
              .map(
                delete => WriteRequest.builder().deleteRequest(delete).build()
              )
              .asJavaCollection

            dynamo
              .batchWriteItem(
                BatchWriteItemRequest
                  .builder()
                  .requestItems(
                    Map(
                      DynamoElasticsearchExternalIdMapping.TableName -> deletes
                    ).asJava
                  )
                  .build()
              )
              .toScala
              .map(_ => {})
          }
        )
        .force
    }
  }

  private def makeExternalIdKey(
    esExternalId: EsExternalId,
    itemType: ItemType
  ): AttributeValue = {
    s"$esExternalId:$itemType".toAttributeValue
  }

  private def parseKey(key: String): Option[(EsExternalId, ItemType)] = {
    Try {
      key.splitAt(key.lastIndexOf(':')) match {
        case (externalId, itemTypeString) =>
          EsExternalId.parse(externalId) -> ItemType.fromString(
            itemTypeString.stripPrefix(":")
          )
      }
    } match {
      case Failure(exception) =>
        logger.error(
          s"Malformed key: ${key}",
          exception
        )

        None

      case Success(value) => Some(value)
    }
  }
}
