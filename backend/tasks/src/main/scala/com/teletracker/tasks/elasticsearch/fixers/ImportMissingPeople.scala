package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.PersonImportHandler
import com.teletracker.common.process.tmdb.PersonImportHandler.PersonImportHandlerArgs
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class ImportMissingPeople @Inject()(
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
            .grouped(4)
            .delayedMapF(1 second, scheduler)(group => {
              if (continue) {
                total.addAndGet(group.size)
                Future
                  .sequence(group.map(person => {
                    personImportHandler.handleItem(
                      PersonImportHandlerArgs(
                        dryRun,
                        scheduleDenorm = !dryRun
                      ),
                      person
                    )
                  }))
                  .map(_ => {})
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

    logger.info(s"Would've updated a total of ${total.get()} missing ids")
  }
}
