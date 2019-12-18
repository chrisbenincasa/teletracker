package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.ThingType
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.scraper.model.WhatsOnNetflixCatalogItem
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import javax.inject.Inject
import io.circe.syntax._
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI

class TransformWhatsOnNetflixCatalog @Inject()(sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")

    val source = sourceRetriever.getSource(input)

    val output = new File("whats-on-netflix-movies.txt")
    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(output))
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
            .map(item => {
              NetflixCatalogItem(
                availableDate = None,
                title = item.title.trim,
                releaseYear = Some(item.titlereleased.toInt),
                network = "Netflix",
                `type` = item.`type` match {
                  case "Movie" => ThingType.Movie
                  case "TV"    => ThingType.Show
                  case _       => throw new IllegalArgumentException
                },
                externalId = Some(item.netflixid)
              )
            })
            .foreach(item => {
              writer.println(item.asJson.noSpaces)
            })
      }
    } finally {
      source.close()
    }

    writer.flush()
    writer.close()
  }
}
