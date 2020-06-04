package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.ElasticsearchExecutor
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import java.net.URI
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class DeleteMovies @Inject()(
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val inputFile = rawArgs.valueOrThrow[URI]("input")
    val limit = rawArgs.valueOrDefault("limit", -1)

    sourceRetriever
      .getSourceStream(inputFile)
      .foreach(source => {
        try {
          source
            .getLines()
            .safeTake(limit)
            .foreach(line => {
              val query = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.termQuery("type", "movie"))
                .must(
                  QueryBuilders.termQuery(
                    "external_ids",
                    EsExternalId(ExternalSource.TheMovieDb, line).toString
                  )
                )

              val request = new DeleteByQueryRequest(
                teletrackerConfig.elasticsearch.items_index_name
              ).setQuery(query).setRefresh(true)

              elasticsearchExecutor.deleteByQuery(request).recover {
                case NonFatal(e) =>
                  logger.error("Unexpected error while deleting", e)
              } await ()
            })
        } finally {
          source.close()
        }
      })
  }
}
