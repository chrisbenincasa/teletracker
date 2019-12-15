package com.teletracker.tasks.tmdb

import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.http.{BaseHttp4sClient, HttpRequest}
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  PersonDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceWriter
import io.circe.{Decoder, Encoder}
import javax.inject.Inject
import java.io.{
  BufferedOutputStream,
  FileInputStream,
  FileOutputStream,
  PipedInputStream,
  PipedOutputStream,
  PrintWriter,
  Writer
}
import io.circe.parser._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.sys.process._

case class DumpAllIdsArgs(
  itemType: String,
  date: LocalDate,
  local: Boolean)

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
    extends TeletrackerTask {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private val blockingExecCtx =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

  private val blocker: Blocker = Blocker.liftExecutionContext(blockingExecCtx)

  override type TypedArgs = DumpAllIdsArgs

  implicit override protected lazy val typedArgsEncoder
    : Encoder[DumpAllIdsArgs] =
    io.circe.generic.semiauto.deriveEncoder[DumpAllIdsArgs]

  override def preparseArgs(args: Args): DumpAllIdsArgs =
    DumpAllIdsArgs(
      itemType = args.valueOrDefault("itemType", "movie"),
      date = args.valueOrDefault("date", LocalDate.now()),
      local = args.valueOrDefault("local", false)
    )

  override protected def runInternal(args: Args): Unit = {
    val DumpAllIdsArgs(itemType, date, local) = preparseArgs(args)

    val sanitizedTypes = itemType match {
      case "all"  => Seq("movie", "tv_series", "person")
      case "show" => Seq("tv_series")
      case x      => Seq(x)
    }

    sanitizedTypes.foreach(sanitizedType => {
      val outputLocation = handleType(sanitizedType, date)

      val destination = if (local) {
        URI.create(
          s"file://${System.getProperty("user.dir")}/${sanitizedType}_ids_sorted-${date}.json.gz"
        )
      } else {
        URI.create(
          s"s3://${teletrackerConfig.data.s3_bucket}/scrape-results/tmdb/${date}/${sanitizedType}_ids_sorted-${date}.json.gz"
        )
      }

      sourceWriter.writeFile(destination, outputLocation)

      outputLocation.toFile.delete()
    })
  }

  private def handleType(
    itemType: String,
    date: LocalDate
  ) = {
    require(Set("movie", "tv_series", "person").contains(itemType))

    val client = new BaseHttp4sClient(blocker)

    val tmpFile = Files.createTempFile(itemType, ".tmp")
    tmpFile.toFile.deleteOnExit()

    logger.info(s"Outputting to ${tmpFile.toAbsolutePath}")

    client
      .toFile(
        "http://files.tmdb.org",
        HttpRequest(DumpAllIds.getIdsPath(itemType, date)),
        tmpFile.toFile,
        blocker
      )
      .unsafeRunSync()

    val src = Source.fromInputStream(
      new GZIPInputStream(new FileInputStream(tmpFile.toFile))
    )

    val tmpTsv = Files.createTempFile(itemType, ".tsv.tmp")
    tmpTsv.toFile.deleteOnExit()

    logger.info(s"Translating to TSV at ${tmpTsv.toAbsolutePath}")

    val tsvOutput = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(tmpTsv.toFile))
    )

    val lineHandler = itemType match {
      case "movie" =>
        handleDumpType[MovieDumpFileRow](_, makeTsvLine, tsvOutput)
      case "tv_series" =>
        handleDumpType[TvShowDumpFileRow](_, makeTsvLine, tsvOutput)
      case "person" =>
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

    val sortedJson = Files.createTempFile(s"${itemType}_sorted", ".tmp.gz")
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
      case "movie" =>
        line => handleTsvLine(line, tsvToMovieDump, sortedJsonOut)

      case "tv_series" =>
        line => handleTsvLine(line, tsvToTvShowDump, sortedJsonOut)

      case "person" =>
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
