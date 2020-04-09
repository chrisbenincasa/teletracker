package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.PersonImportHandler
import com.teletracker.common.process.tmdb.PersonImportHandler.PersonImportHandlerArgs
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import io.circe.generic.JsonCodec
import javax.inject.Inject
import org.elasticsearch.action.bulk.BulkRequest
import java.net.URI
import io.circe.syntax._
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class ImportMissingPeople @Inject()(
  teletrackerConfig: TeletrackerConfig,
  sourceRetriever: SourceRetriever,
  personImportHandler: PersonImportHandler
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val missingIds = args.valueOrThrow[URI]("missingIds")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault[Int]("limit", -1)
    val initialFileOffset = args.value[Int]("initialFileOffset")
    val perFileLimit = args.valueOrDefault[Int]("perFileLimit", -1)
    val missingOnly = args.valueOrDefault("missingOnly", true)
    val stopAtId = args.value[Int]("stopAtId")
    val dryRun = args.valueOrDefault("dryRun", true)

    val missingIdsSource = Source.fromURI(missingIds)
    val allMissingIds = missingIdsSource.getLines().map(_.toInt).toSet
    missingIdsSource.close()

    val total = new AtomicInteger()
    var continue = true

    val rotator = FileRotator.everyNBytes(
      "people-import-2",
      StorageUnit.fromMegabytes(50),
      Some("people-import-2")
    )

    sourceRetriever
      .getSourceStream(input, offset = offset, limit = limit)
      .foreach(source => {
        try {
          new IngestJobParser()
            .asyncStream[Person](source.getLines())
            .collect {
              case Right(value) if !missingOnly || allMissingIds(value.id) =>
                value
            }
            .applyOptional(initialFileOffset)(_.drop(_))
            .safeTake(perFileLimit)
            .applyOptional(stopAtId)(
              (stream, id) =>
                stream.withEffect(p => {
                  if (p.id == id) {
                    continue = false
                  }
                })
            )
//            .foreachConcurrent(8)(_ => {
//              Future {
//                total.incrementAndGet()
//              }
//            })
            .grouped(10)
            .delayedMapF(1 second, scheduler)(group => {
              if (continue) {
                total.addAndGet(group.size)
                Future
                  .sequence(group.map(person => {
                    personImportHandler.handleItem(
                      PersonImportHandlerArgs(
                        dryRun
                      ),
                      person
                    )
                  }))
                  .map(result => {
                    val lines = result
                      .collect {
                        case PersonImportHandler.PersonInsertResult(
                            personId,
                            _,
                            Some(jsonResult)
                            ) =>
                          EsBulkIndex(
                            teletrackerConfig.elasticsearch.people_index_name,
                            personId,
                            jsonResult
                          )

                        case PersonImportHandler.PersonUpdateResult(
                            personId,
                            _,
                            _,
                            _,
                            _,
                            Some(jsonResult)
                            ) =>
                          EsBulkUpdate(
                            teletrackerConfig.elasticsearch.people_index_name,
                            personId,
                            jsonResult
                          )
                      }
                      .flatMap(_.lines)

                    rotator.writeLines(lines)
                  })
              } else {
                Future.unit
              }
            })
            .force
            .await()
        } finally {
          source.close()
        }
      })

    rotator.finish()

    logger.info(s"Would've updated a total of ${total.get()} missing ids")
  }
}

class SplitFile @Inject()(
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends TeletrackerTaskWithDefaultArgs {
  import io.circe.parser._
  override protected def runInternal(args: Args): Unit = {
    val rotator = FileRotator.everyNBytes(
      "people-import-2-smaller",
      StorageUnit.fromMegabytes(10),
      Some("people-import-smaller")
    )

    val input = args.valueOrThrow[URI]("input")
    sourceRetriever
      .getSourceStream(input)
      .foreach(source => {
        try {
          source
            .getLines()
            .toStream
            .foreach(rotator.writeLine)
        } finally {
          source.close()
        }
      })

    rotator.finish()
  }
}

class FixUpdateLines @Inject()(
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends TeletrackerTaskWithDefaultArgs {
  import io.circe.parser._
  override protected def runInternal(args: Args): Unit = {
    val rotator = FileRotator.everyNBytes(
      "people-import-updates-only",
      StorageUnit.fromMegabytes(10),
      Some("people-import-updates-only")
    )

    val input = args.valueOrThrow[URI]("input")
    sourceRetriever
      .getSourceStream(input)
      .foreach(source => {
        try {
          source
            .getLines()
            .toStream
            .grouped(2)
            .map(_.toList)
            .foreach {
              case op :: doc :: Nil =>
                parse(op).foreach(json => {
                  json.asObject
                    .filter(_.contains("update"))
                    .foreach(_ => {
                      parse(doc).foreach(docJson => {
                        rotator.writeLines(
                          Seq(op, Map("doc" -> docJson).asJson.noSpaces)
                        )
                      })
                    })
                })
              case _ =>
            }
        } finally {
          source.close()
        }
      })

    rotator.finish()
  }
}

trait EsBulkOp {
  def typ: String
  def lines: Seq[String]
}

@JsonCodec
case class EsBulkIndexRaw(index: EsBulkIndexDoc)

@JsonCodec
case class EsBulkIndexDoc(
  _index: String,
  _id: String)

@JsonCodec
case class EsBulkUpdateRaw(update: EsBulkIndexDoc)

case class EsBulkIndex(
  index: String,
  id: UUID,
  doc: String)
    extends EsBulkOp {
  override def typ: String = "index"

  override def lines: Seq[String] = Seq(
    Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces,
    doc
  )
}

@JsonCodec
case class EsBulkUpdate(
  index: String,
  id: UUID,
  update: String)
    extends EsBulkOp {
  override def typ: String = "update"

  override def lines: Seq[String] = Seq(
    Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces,
    update
  )
}

@JsonCodec
case class EsBulkDelete(
  index: String,
  id: UUID)
    extends EsBulkOp {
  override def typ: String = "delete"

  override def lines: Seq[String] =
    Seq(
      Map(typ -> Map("_index" -> index, "_id" -> id.toString).asJson).asJson.noSpaces
    )
}
