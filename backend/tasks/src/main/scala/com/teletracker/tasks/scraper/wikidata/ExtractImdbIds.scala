package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.io.{
  BufferedOutputStream,
  File,
  FileOutputStream,
  PrintStream,
  PrintWriter
}
import java.net.URI

class ExtractImdbIds @Inject()(sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val loc = args.valueOrThrow[URI]("loc")
    val itemType = args.valueOrDefault[ItemType]("itemType", ItemType.Movie)
    val outputPath = args.valueOrThrow[String]("outputPath")

    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(new File(outputPath)))
    )

    sourceRetriever
      .getSourceStream(loc)
      .foreach(source => {
        try {
          new IngestJobParser()
            .stream[EsItemDumpRow](source.getLines())
            .collect {
              case Right(value) if value._source.`type` == itemType => value
            }
            .foreach(row => {
              row._source.externalIdsGrouped
                .get(ExternalSource.Imdb)
                .filter(_.nonEmpty)
                .filterNot(_ == "0")
                .foreach(writer.println)
            })

        } finally {
          source.close()
        }
      })
  }
}
