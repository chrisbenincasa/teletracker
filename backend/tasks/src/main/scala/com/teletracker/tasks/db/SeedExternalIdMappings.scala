package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.lookups.DynamoElasticsearchExternalIdMapping
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.model.{EsItemDumpRow, EsPersonDumpRow}
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  BatchWriteItemRequest,
  PutRequest,
  WriteRequest
}
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success
import scala.util.control.NonFatal

class SeedExternalIdMappings @Inject()(
  dynamo: DynamoDbAsyncClient,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val dumpLoc = args.valueOrThrow[URI]("input")
    val limit = args.valueOrDefault[Int]("limit", -1)

    val processed = new AtomicLong(0)

    sourceRetriever
      .getSourceStream(dumpLoc)
      .safeTake(limit)
      .foreach(source => {
        try {
          new IngestJobParser()
            .asyncStream[EsItemDumpRow](source.getLines())
            .collect {
              case Right(value) => value._source
            }
            .safeTake(limit)
            .flatMapSeq(item => {
              item.external_ids
                .getOrElse(Nil)
                .flatMap(externalId => {
                  Option(externalId.id)
                    .filter(_.nonEmpty)
                    .filterNot(_ == "0")
                    .map(_ => s"$externalId:${item.`type`}" -> item.id)
                })
            })
            .grouped(25)
            .delayedMapF(50 millis, scheduler)(batch => {
              val puts = batch.map {
                case (externalId, id) =>
                  WriteRequest
                    .builder()
                    .putRequest(
                      PutRequest
                        .builder()
                        .item(
                          Map(
                            "externalId" -> externalId.toAttributeValue,
                            "id" -> id.toAttributeValue
                          ).asJava
                        )
                        .build()
                    )
                    .build()
              }

              val req = BatchWriteItemRequest
                .builder()
                .requestItems(
                  Map(
                    DynamoElasticsearchExternalIdMapping.TableName -> puts.asJava
                  ).asJava
                )
                .build()

              dynamo
                .batchWriteItem(req)
                .toScala
                .andThen {
                  case Success(value) =>
                    val total = processed.addAndGet(25)
                    if (total % 1000 == 0) {
                      logger.info(s"Processed ${total} items so far")
                    }
                }
                .recover {
                  case NonFatal(e) =>
                    logger.warn(s"Could write batch: ${batch}")
                }
            })
            .force
            .await()
        } finally {
          source.close()
        }
      })

//    writer.flush()
//    writer.close()
  }
}

class SeedPersonExternalIdMappings @Inject()(
  dynamo: DynamoDbAsyncClient,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val dumpLoc = args.valueOrThrow[URI]("input")
    val limit = args.valueOrDefault[Int]("limit", -1)

    val processed = new AtomicLong(0)

    sourceRetriever
      .getSourceStream(dumpLoc)
      .safeTake(limit)
      .foreach(source => {
        try {
          new IngestJobParser()
            .asyncStream[EsPersonDumpRow](source.getLines())
            .collect {
              case Right(value) => value._source
            }
            .safeTake(limit)
            .flatMapSeq(item => {
              item.external_ids
                .getOrElse(Nil)
                .flatMap(externalId => {
                  Option(externalId.id)
                    .filter(_.nonEmpty)
                    .filterNot(_ == "0")
                    .map(_ => s"$externalId:${ItemType.Person}" -> item.id)
                })
            })
            .grouped(25)
            .delayedMapF(50 millis, scheduler)(batch => {
              val puts = batch.map {
                case (externalId, id) =>
                  WriteRequest
                    .builder()
                    .putRequest(
                      PutRequest
                        .builder()
                        .item(
                          Map(
                            "externalId" -> externalId.toAttributeValue,
                            "id" -> id.toAttributeValue
                          ).asJava
                        )
                        .build()
                    )
                    .build()
              }

              val req = BatchWriteItemRequest
                .builder()
                .requestItems(
                  Map(
                    DynamoElasticsearchExternalIdMapping.TableName -> puts.asJava
                  ).asJava
                )
                .build()

              dynamo
                .batchWriteItem(req)
                .toScala
                .andThen {
                  case Success(_) =>
                    val total = processed.addAndGet(25)
                    if (total % 1000 == 0) {
                      logger.info(s"Processed ${total} items so far")
                    }
                }
                .recover {
                  case NonFatal(e) =>
                    logger.warn(s"Could write batch: ${batch}")
                }
            })
            .force
            .await()
        } finally {
          source.close()
        }
      })

    //    writer.flush()
    //    writer.close()
  }
}
