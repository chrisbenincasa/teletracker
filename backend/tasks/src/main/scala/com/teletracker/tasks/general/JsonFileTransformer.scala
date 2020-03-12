package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
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
    extends TeletrackerTaskWithDefaultArgs {
  override def preparseArgs(args: Args): TypedArgs = {
    Map(
      "source" -> args.valueOrThrow[String]("source"),
      "destination" -> args.valueOrThrow[String]("destination")
    )
  }

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("source")
    val destination = args.valueOrThrow[URI]("destination")

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
