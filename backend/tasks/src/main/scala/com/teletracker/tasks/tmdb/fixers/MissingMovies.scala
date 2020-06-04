package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.tasks.model.MovieDumpFileRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.syntax._
import javax.inject.Inject
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI

class MissingMovies @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends UntypedTeletrackerTask {
  override def runInternal(): Unit = {
    val movieExportFile = rawArgs.value[URI]("moveExportFile").get
    val dbDumpFile = rawArgs.value[URI]("dbDumpFile").get

    val movieExportSource = sourceRetriever.getSource(movieExportFile)
    val dumpSource = sourceRetriever.getSource(dbDumpFile)

    val movieIds = dumpSource.getLines().toSet

    val file = new File("missing_ids.txt")
    val printer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(file))
    )

    val total = ingestJobParser
      .stream[MovieDumpFileRow](
        movieExportSource.getLines()
      )
      .collect {
        case Right(row) =>
          if (!movieIds(row.id.toString)) {
            printer.println(row.id)
            println(
              s"Missing movie id = ${row.id}, name = ${row.original_title}"
            )
            Some(row)
          } else {
            None
          }
      }
      .flatten
      .size

    println(s"Missing a total of ${total} movies")

    printer.flush()
    printer.close()
    movieExportSource.close()
    dumpSource.close()
  }
}

class FilterToMissingMovies @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends UntypedTeletrackerTask {
  override def runInternal(): Unit = {
    val movieExportFile = rawArgs.value[URI]("movieExportFile").get
    val missingMoviesFile = rawArgs.value[URI]("missingMoviesFile").get

    val movieExportSource = sourceRetriever.getSource(movieExportFile)
    val missingMoviesSource = sourceRetriever.getSource(missingMoviesFile)

    val movieIds = missingMoviesSource.getLines().toSet

    val file = new File("filtered_movie_dump.json")
    val printer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(file))
    )

    val total = ingestJobParser
      .stream[MovieDumpFileRow](
        movieExportSource.getLines()
      )
      .flatMap {
        case Right(row) =>
          if (movieIds(row.id.toString)) {
            Some(row)
          } else {
            None
          }
        case _ => None
      }
      .foreach(row => {
        printer.println(row.asJson.noSpaces)
      })

    printer.flush()
    printer.close()
    movieExportSource.close()
    missingMoviesSource.close()
  }
}
