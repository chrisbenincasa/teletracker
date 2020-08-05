package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ElasticsearchExecutor
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.util.SourceUtils
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class DeleteMoviesByTmdbId @Inject()(
  fileUtils: SourceUtils,
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(): Unit = {
    val idsFile = rawArgs.valueOrThrow[URI]("idsFileLocation")
    val limit = rawArgs.valueOrDefault("limit", -1)

    val ids = fileUtils.readAllLinesToSet(idsFile, consultSourceCache = false)
    val total = new AtomicLong()

    AsyncStream
      .fromSeq(ids.toSeq)
      .safeTake(limit)
      .grouped(50)
      .delayedForeachF(500 millis, scheduler)(group => {
        val req = new DeleteByQueryRequest(
          teletrackerConfig.elasticsearch.items_index_name
        ).setQuery(buildQuery(group))

        elasticsearchExecutor
          .deleteByQuery(req)
          .map(resp => {
            resp.getSearchFailures.asScala.foreach(println)
            resp.getBulkFailures.asScala.foreach(println)

            logger.info(s"Deleted ${resp.getDeleted} items")
            total.addAndGet(resp.getDeleted)
            resp.getDeleted
          })
      })
      .await()

    logger.info(s"Deleted a total of ${total.get()} items")
  }

  private def buildQuery(ids: Seq[String]) = {
    val baseQuery = QueryBuilders
      .boolQuery()
      .minimumShouldMatch(1)
      .filter(QueryBuilders.termQuery("type", ItemType.Movie.toString))

    ids
      .map(id => {
        QueryBuilders.termQuery(
          "external_ids",
          EsExternalId(ExternalSource.TheMovieDb, id).toString
        )
      })
      .foldLeft(baseQuery)(_.should(_))
  }
}
