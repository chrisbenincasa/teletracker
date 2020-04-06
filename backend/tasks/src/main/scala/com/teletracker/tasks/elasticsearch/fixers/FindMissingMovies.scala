package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.model.tmdb.Movie
import com.teletracker.tasks.util.SourceRetriever
import io.circe.syntax._
import javax.inject.Inject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class FindMissingMovies @Inject()(
  teletrackerConfig: TeletrackerConfig,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends CreateBackfillUpdateFile[Movie](teletrackerConfig) {
  private val items = ConcurrentHashMap.newKeySet[String]()

  override protected def init(args: Args): Unit = {
    val missingIds = args.valueOrThrow[URI]("missingIdsFile")

    sourceRetriever
      .getSourceStream(missingIds)
      .foreach(source => {
        try {
          items.addAll(source.getLines().toList.asJava)
        } finally {
          source.close()
        }
      })
  }

  override protected def uniqueId(item: Movie): String = item.id.toString

  override protected def shouldKeepItem(item: Movie): Boolean = {
    items.contains(item.id.toString)
  }

  override protected def makeBackfillRow(item: Movie): TmdbBackfillOutputRow = {
    TmdbBackfillOutputRow(item.id, item.asJson)
  }
}
