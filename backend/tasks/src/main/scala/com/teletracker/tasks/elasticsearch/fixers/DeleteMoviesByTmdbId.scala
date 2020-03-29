package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsExternalId
}
import com.teletracker.common.model.tmdb.ExternalIds
import com.teletracker.common.util.Futures._
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.util.FileUtils
import javax.inject.Inject
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.concurrent.duration._

class DeleteMoviesByTmdbId @Inject()(
  fileUtils: FileUtils,
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val idsFile = args.valueOrThrow[URI]("idsFileLocation")
    val limit = args.valueOrDefault("limit", -1)

    val ids = fileUtils.readAllLinesToSet(idsFile)
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
