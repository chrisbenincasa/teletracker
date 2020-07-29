package com.teletracker.tasks.tmdb

import cats.effect.{Blocker, ContextShift, IO}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.http.{BaseHttp4sClient, HttpRequest}
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
import com.teletracker.tasks.model.{
  MovieDumpFileRow,
  PersonDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceWriter
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import javax.inject.Inject
import java.io._
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.sys.process._

@JsonCodec
@GenArgParser
case class DumpAllIdsArgs(
  itemType: String = "movie",
  date: LocalDate = LocalDate.now(),
  local: Boolean = false)

object DumpAllIdsArgs

object DumpAllIds {
  def getIdsPath(
    typePrefix: String,
    date: LocalDate
  ) =
    s"/p/exports/${typePrefix}_ids_${date.format(DateTimeFormatter.ofPattern("MM_dd_yyyy"))}.json.gz"
}

class DumpAllIds @Inject()(
  sourceWriter: SourceWriter,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DumpAllIdsArgs] {
  implicit private val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private val blockingExecCtx =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

  private val blocker: Blocker = Blocker.liftExecutionContext(blockingExecCtx)

  prerun {
    val typesToPull = if (args.itemType == "all") {
      ItemType.values().toSeq
    } else {
      Seq(ItemType.fromString(args.itemType))
    }

    typesToPull
      .map(itemType => {
        TeletrackerTask.taskMessage[AllTmdbIdsDumpDeltaLocator](
          AllTmdbIdsDumpDeltaLocatorArgs(
            seedDumpDate = Some(args.date),
            itemType = itemType
          )
        )
      })
      .foreach(registerFollowupTask)
  }

  override protected def runInternal(): Unit = {
    val DumpAllIdsArgs(itemType, date, local) = args

    val typesToPull = if (itemType == "all") {
      ItemType.values().toSeq
    } else {
      Seq(ItemType.fromString(itemType))
    }

    typesToPull.foreach(typeToPull => {
      val outputLocation = handleType(typeToPull, date)

      val destination = if (local) {
        URI.create(
          s"file://${System.getProperty("user.dir")}/${typeToPull}_ids_sorted-${date}.json.gz"
        )
      } else {
        getOutputS3Path(date, typeToPull)
      }

      sourceWriter.writeFile(destination, outputLocation)

      outputLocation.toFile.delete()
    })
  }

  private def getOutputS3Path(
    date: LocalDate,
    itemType: ItemType
  ): URI = {
    val tmdbType = itemTypeToTmdbType(itemType)
    URI.create(
      s"s3://${teletrackerConfig.data.s3_bucket}/scrape-results/tmdb/${date}/${tmdbType}_ids_sorted-${date}.json.gz"
    )
  }

  private def itemTypeToTmdbType(itemType: ItemType): String =
    itemType match {
      case ItemType.Movie  => "movie"
      case ItemType.Show   => "tv_series"
      case ItemType.Person => "person"
    }

  private def handleType(
    itemType: ItemType,
    date: LocalDate
  ) = {
    val tmdbType = itemTypeToTmdbType(itemType)

    val client = new BaseHttp4sClient(blocker)

    val tmpFile = Files.createTempFile(tmdbType, ".tmp")
    tmpFile.toFile.deleteOnExit()

    logger.info(s"Outputting to ${tmpFile.toAbsolutePath}")

    client
      .toFile(
        "http://files.tmdb.org",
        HttpRequest(DumpAllIds.getIdsPath(tmdbType, date)),
        tmpFile.toFile,
        blocker
      )
      .unsafeRunSync()

    val src = Source.fromInputStream(
      new GZIPInputStream(new FileInputStream(tmpFile.toFile))
    )

    val tmpTsv = Files.createTempFile(tmdbType, ".tsv.tmp")
    tmpTsv.toFile.deleteOnExit()

    logger.info(s"Translating to TSV at ${tmpTsv.toAbsolutePath}")

    val tsvOutput = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(tmpTsv.toFile))
    )

    val lineHandler = itemType match {
      case ItemType.Movie =>
        handleDumpType[MovieDumpFileRow](_, makeTsvLine, tsvOutput)
      case ItemType.Show =>
        handleDumpType[TvShowDumpFileRow](_, makeTsvLine, tsvOutput)
      case ItemType.Person =>
        handleDumpType[PersonDumpFileRow](_, makeTsvLine, tsvOutput)
    }

    try {
      src
        .getLines()
        .foreach(lineHandler)
    } finally {
      src.close()
    }

    tsvOutput.flush()

    val sortedJson = Files.createTempFile(s"${tmdbType}_sorted", ".tmp.gz")
    logger.info(s"Translating to sorted JSON at ${sortedJson.toAbsolutePath}")

    val sortedJsonOut = new PrintWriter(
      new BufferedOutputStream(
        new GZIPOutputStream(new FileOutputStream(sortedJson.toFile))
      )
    )

    val sortedIn = new PipedOutputStream()
    val sortedOut = new PipedInputStream(sortedIn)

    Future {
      List(
        "sort",
        "-k3,3",
        "--field-separator=\t",
        "-n",
        "-r"
      ).#<(tmpTsv.toFile).#>(sortedIn).!
    }

    val tsvLineHandler: String => Unit = itemType match {
      case ItemType.Movie =>
        line => handleTsvLine(line, tsvToMovieDump, sortedJsonOut)

      case ItemType.Show =>
        line => handleTsvLine(line, tsvToTvShowDump, sortedJsonOut)

      case ItemType.Person =>
        line => handleTsvLine(line, tsvToPersonDump, sortedJsonOut)
    }

    Source
      .fromInputStream(sortedOut)
      .getLines()
      .foreach(tsvLineHandler)

    sortedJsonOut.flush()
    sortedJsonOut.close()

    sortedJson
  }

  private def handleDumpType[T: Decoder](
    line: String,
    toTsv: T => String,
    writeTo: PrintWriter
  ): Unit = {
    decode[T](line)
      .map(toTsv)
      .foreach(writeTo.println)
  }

  private def handleTsvLine[T: Encoder](
    line: String,
    fromTsv: String => T,
    writeTo: PrintWriter
  ): Unit = {
    writeTo.println(fromTsv(line).asJson.noSpaces)
  }

  private def makeTsvLine(movieDumpFileRow: MovieDumpFileRow): String = {
    List(
      movieDumpFileRow.id,
      movieDumpFileRow.original_title.replaceAllLiterally("\t", ""),
      movieDumpFileRow.popularity,
      movieDumpFileRow.video,
      movieDumpFileRow.adult
    ).mkString("\t")
  }

  private def makeTsvLine(tvShowDumpFileRow: TvShowDumpFileRow): String = {
    List(
      tvShowDumpFileRow.id,
      tvShowDumpFileRow.original_name.replaceAllLiterally("\t", ""),
      tvShowDumpFileRow.popularity
    ).mkString("\t")
  }

  private def makeTsvLine(personDumpFileRow: PersonDumpFileRow): String = {
    List(
      personDumpFileRow.id,
      personDumpFileRow.name.replaceAllLiterally("\t", ""),
      personDumpFileRow.popularity,
      personDumpFileRow.adult
    ).mkString("\t")
  }

  private def tsvToMovieDump(line: String): MovieDumpFileRow = {
    val Array(id, title, popularity, video, adult) = line.split("\t", 5)

    MovieDumpFileRow(
      adult = adult.equalsIgnoreCase("true"),
      id = id.toInt,
      original_title = title,
      popularity = popularity.toDouble,
      video = video.equalsIgnoreCase("true")
    )
  }

  private def tsvToTvShowDump(line: String): TvShowDumpFileRow = {
    val Array(id, title, popularity) = line.split("\t", 3)

    TvShowDumpFileRow(
      id = id.toInt,
      original_name = title,
      popularity = popularity.toDouble
    )
  }

  private def tsvToPersonDump(line: String): PersonDumpFileRow = {
    val Array(id, title, popularity, adult) = line.split("\t", 4)

    PersonDumpFileRow(
      id = id.toInt,
      name = title,
      popularity = popularity.toDouble,
      adult = adult.equalsIgnoreCase("true")
    )
  }
}
