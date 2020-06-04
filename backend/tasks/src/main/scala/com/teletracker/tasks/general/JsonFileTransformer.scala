package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.Json
import javax.inject.Inject
import java.io.{BufferedOutputStream, FileOutputStream, PrintStream}
import java.net.URI
import java.nio.file.Files

class JsonFileTransformer @Inject()(
  sourceRetriever: SourceRetriever,
  sourceWriter: SourceWriter,
  ingestJobParser: IngestJobParser)
    extends UntypedTeletrackerTask {
  override def preparseArgs(args: RawArgs): ArgsType = {
    Map(
      "source" -> args.valueOrThrow[String]("source"),
      "destination" -> args.valueOrThrow[String]("destination")
    )
  }

  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("source")
    val destination = rawArgs.valueOrThrow[URI]("destination")

    val source = sourceRetriever.getSource(input)

    val tmp = Files.createTempFile("json_convert", "tmp")
    tmp.toFile.deleteOnExit()

    val writer = new PrintStream(
      new BufferedOutputStream(new FileOutputStream(tmp.toFile))
    )

    ingestJobParser
      .parse[Json](source.getLines(), IngestJobParser.AllJson) match {
      case Left(value) =>
        throw value

      case Right(value) =>
        value.foreach(line => writer.println(line.noSpaces))
    }

    writer.flush()
    writer.close()

    sourceWriter.writeFile(destination, tmp)
  }
}
