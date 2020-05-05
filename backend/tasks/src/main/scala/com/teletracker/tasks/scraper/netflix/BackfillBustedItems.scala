package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemUpdater
import com.teletracker.common.process.tmdb.{
  ItemExpander,
  MovieImportHandler,
  TvShowImportHandler
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.model.MatchInput
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class BackfillBustedItems @Inject()(
  sourceRetriever: SourceRetriever,
  itemExpander: ItemExpander,
  movieImportHandler: MovieImportHandler,
  tvShowImportHandler: TvShowImportHandler
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val limit = args.valueOrDefault("limit", -1)

    new IngestJobParser()
      .stream[MatchInput[NetflixScrapedCatalogItem]](
        sourceRetriever.getSource(input).getLines()
      )
      .safeTake(limit)
      .foreach {
        case Left(value) => logger.error("bad", value)
        case Right(value) =>
          val itemId = value.esItem.id
          val tmdbId =
            value.esItem.externalIdsGrouped(ExternalSource.TheMovieDb)

          try {
            value.esItem.`type` match {
              case ItemType.Movie =>
                itemExpander
                  .expandMovie(tmdbId.toInt)
                  .flatMap(movie => {
                    movieImportHandler.updateMovie(
                      itemId,
                      movie,
                      MovieImportHandler.MovieImportHandlerArgs(
                        forceDenorm = false,
                        dryRun = false
                      )
                    )
                  })
                  .await()

              case ItemType.Show =>
                itemExpander
                  .expandTvShow(tmdbId.toInt)
                  .flatMap(show => {
                    tvShowImportHandler.updateShow(
                      itemId,
                      show,
                      TvShowImportHandler.TvShowImportHandlerArgs(
                        dryRun = false
                      )
                    )
                  })
                  .await()

              case ItemType.Person => throw new IllegalStateException("")
            }
          } catch {
            case NonFatal(e) =>
              logger.warn(s"Unexpected error, id = $itemId", e)
          }
      }
  }
}
