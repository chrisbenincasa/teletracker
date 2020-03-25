package com.teletracker.tasks.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.EsPerson
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  PersonDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI

class MissingPeople @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends TeletrackerTaskWithDefaultArgs {
  implicit protected val tDecoder: Codec[MovieDumpFileRow] =
    deriveCodec

  override protected def runInternal(args: Args): Unit = {
    val peopleExportFile = args.value[URI]("peopleExportFile").get
    val dbDumpFile = args.value[URI]("dbDumpFile").get

    val personIdsInDb =
      sourceRetriever.getSourceStream(dbDumpFile).foldLeft(Set.empty[String]) {
        case (acc, source) =>
          try {
            acc ++ ingestJobParser
              .stream[EsPersonDumpRow](source.getLines())
              .flatMap {
                case Left(value) =>
                  logger.error("Could not parse line", value)
                  None
                case Right(value) =>
                  value._source.externalIdsGrouped
                    .get(ExternalSource.TheMovieDb)
              }
              .toSet
          } finally {
            source.close()
          }
      }

    val file = new File("missing_ids.txt")
    val printer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(file))
    )

    val peopleExportSource = sourceRetriever.getSource(peopleExportFile)

    val total = ingestJobParser
      .stream[PersonDumpFileRow](
        peopleExportSource.getLines()
      )
      .collect {
        case Right(row) =>
          if (!personIdsInDb(row.id.toString)) {
            printer.println(row.id)
            println(
              s"Missing person id = ${row.id}, name = ${row.name}"
            )
            Some(row)
          } else {
            None
          }
      }
      .flatten
      .size

    println(s"Missing a total of ${total} people")

    printer.flush()
    printer.close()
    peopleExportSource.close()
  }
}

@JsonCodec
case class EsPersonDumpRow(_source: EsPerson)
