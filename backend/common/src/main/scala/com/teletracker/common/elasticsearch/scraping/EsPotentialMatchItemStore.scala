package com.teletracker.common.elasticsearch.scraping

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsPotentialMatchResponse
}
import com.teletracker.common.elasticsearch.model.EsPotentialMatchItem
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.util.{AsyncStream, HasId}
import io.circe.Codec
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import com.teletracker.common.util.Functions._
import org.apache.lucene.search.join.ScoreMode
import scala.concurrent.{ExecutionContext, Future}

class EsPotentialMatchItemStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchCrud[EsPotentialMatchItem]
    with ElasticsearchAccess {
  override protected val indexName: String =
    teletrackerConfig.elasticsearch.potential_matches_index_name

  def search(
    request: PotentialMatchItemSearch
  ): Future[EsPotentialMatchResponse] = {
    val query = QueryBuilders
      .boolQuery()
      .applyOptional(request.scraperType)(
        (builder, typ) =>
          builder.must(
            QueryBuilders
              .nestedQuery(
                "scraped",
                QueryBuilders.termQuery("scraped.type", typ.toString),
                ScoreMode.Avg
              )
          )
      )

    val searchRequest =
      new SearchRequest(indexName)
        .source(new SearchSourceBuilder().query(query).size(request.limit))

    elasticsearchExecutor
      .search(searchRequest)
      .map(response => {
        val items = decodeSearchResponse[EsPotentialMatchItem](response)
        EsPotentialMatchResponse(items, response.getHits.getTotalHits.value)
      })
  }
}

case class PotentialMatchItemSearch(
  scraperType: Option[ScrapeItemType],
  limit: Int)

abstract class ElasticsearchCrud[T: Codec](
  implicit hasId: HasId[T],
  executionContext: ExecutionContext) {
  protected def indexName: String
  protected def elasticsearchExecutor: ElasticsearchExecutor

  def index(item: T): Future[IndexResponse] = {
    elasticsearchExecutor.index(
      new IndexRequest(indexName)
        .create(true)
        .id(hasId.idString(item))
        .source(item.asJson.noSpaces, XContentType.JSON)
    )
  }

  def indexBatch(items: List[T]): Future[Unit] = {
    AsyncStream
      .fromStream(items.grouped(50).toStream)
      .foreachF(batch => {
        val bulkRequest = new BulkRequest()
        batch.foreach(
          insert =>
            bulkRequest.add(
              new IndexRequest(indexName)
                .id(hasId.idString(insert))
                .create(true)
                .source(insert.asJson.noSpaces, XContentType.JSON)
            )
        )

        elasticsearchExecutor.bulk(bulkRequest).map(_ => {})
      })
  }

  def update(item: T): Future[UpdateResponse] = {
    elasticsearchExecutor.update(
      new UpdateRequest(indexName, hasId.idString(item))
        .doc(item.asJson.noSpaces, XContentType.JSON)
    )
  }

  def upsertBatch(items: List[T]): Future[Unit] = {
    AsyncStream
      .fromStream(items.grouped(25).toStream)
      .foreachF(batch => {
        val bulkRequest = new BulkRequest()
        batch.foreach(
          insert =>
            bulkRequest.add(
              new UpdateRequest(indexName, hasId.idString(insert))
                .id(hasId.idString(insert))
                .doc(insert.asJson.noSpaces, XContentType.JSON)
                .upsert(insert.asJson.noSpaces, XContentType.JSON)
            )
        )

        elasticsearchExecutor.bulk(bulkRequest).map(_ => {})
      })
  }

}
