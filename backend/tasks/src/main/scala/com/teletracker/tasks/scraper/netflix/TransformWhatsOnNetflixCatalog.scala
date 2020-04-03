package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.scraper.model.WhatsOnNetflixCatalogItem
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import javax.inject.Inject
import io.circe.syntax._
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

object TransformWhatsOnNetflixCatalog {
  def convert(item: WhatsOnNetflixCatalogItem): NetflixCatalogItem = {
    NetflixCatalogItem(
      availableDate = None,
      title = item.title.trim,
      releaseYear = Option(item.titlereleased).filter(_.nonEmpty).map(_.toInt),
      network = "Netflix",
      `type` = item.`type` match {
        case "Movie" | "Documentary"  => ItemType.Movie
        case "TV" | "Stand-Up Comedy" => ItemType.Show
        case x =>
          throw new IllegalArgumentException(
            s"Encountered unexpected type = $x.\n${item.asJson.spaces2}"
          )
      },
      externalId = Some(item.netflixid),
      description = Some(item.description).filter(_.nonEmpty)
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

    val tmpLocation = Files.createTempFile("whats-on-netflix-transform", "tmp")
    tmpLocation.toFile.deleteOnExit()

    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(tmpLocation.toFile))
    )

    val seen = ConcurrentHashMap.newKeySet[String]()

    sourceRetriever
      .getSourceStream(input)
      .foreach(source => {
        try {
          new IngestJobParser().parse[WhatsOnNetflixCatalogItem](
            source.getLines(),
            IngestJobParser.JsonPerLine
          ) match {
            case Left(value) =>
              throw value
            case Right(value) =>
              value
                .map(TransformWhatsOnNetflixCatalog.convert)
                .filter(_.externalId.forall(seen.add))
                .foreach(item => {
                  writer.println(item.asJson.noSpaces)
                })
          }
        } finally {
          source.close()
        }
      })

    writer.flush()
    writer.close()

    sourceWriter.writeFile(destination, tmpLocation)
  }
}
