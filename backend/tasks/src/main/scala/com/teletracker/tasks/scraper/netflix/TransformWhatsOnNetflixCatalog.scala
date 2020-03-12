package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.db.model.ThingType
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.scraper.model.WhatsOnNetflixCatalogItem
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import javax.inject.Inject
import io.circe.syntax._
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.nio.file.Files

object TransformWhatsOnNetflixCatalog {
  def convert(item: WhatsOnNetflixCatalogItem) = {
    NetflixCatalogItem(
      availableDate = None,
      title = item.title.trim,
      releaseYear = Option(item.titlereleased).filter(_.nonEmpty).map(_.toInt),
      network = "Netflix",
      `type` = item.`type` match {
        case "Movie" | "Documentary"  => ThingType.Movie
        case "TV" | "Stand-Up Comedy" => ThingType.Show
        case x =>
          throw new IllegalArgumentException(
            s"Encountered unexpected type = $x.\n${item.asJson.spaces2}"
          )
      },
      externalId = Some(item.netflixid)
    )
  }
}

class TransformWhatsOnNetflixCatalog @Inject()(
  sourceRetriever: SourceRetriever,
  sourceWriter: SourceWriter)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("source")
    val destination = args.valueOrThrow[URI]("destination")

    val source = sourceRetriever.getSource(input)

    val tmpLocation = Files.createTempFile("whats-on-netflix-transform", "tmp")
    tmpLocation.toFile.deleteOnExit()

    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(tmpLocation.toFile))
    )

    try {
      new IngestJobParser().parse[WhatsOnNetflixCatalogItem](
        source.getLines(),
        IngestJobParser.AllJson
      ) match {
        case Left(value) =>
          throw value
        case Right(value) =>
          value
            .map(TransformWhatsOnNetflixCatalog.convert)
            .foreach(item => {
              writer.println(item.asJson.noSpaces)
            })
      }
    } finally {
      source.close()
    }

    writer.flush()
    writer.close()

    sourceWriter.writeFile(destination, tmpLocation)
  }
}
