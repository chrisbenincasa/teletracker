package com.teletracker.common.elasticsearch.scraping

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.{Bookmark, SearchScore, SortMode}
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsPotentialMatchResponse
}
import com.teletracker.common.elasticsearch.model.{
  EsPotentialMatchItem,
  EsPotentialMatchState
}
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
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.search.sort.SortOrder
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

class EsPotentialMatchItemStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchCrud[String, EsPotentialMatchItem]
    with ElasticsearchAccess {
  override protected val indexName: String =
    teletrackerConfig.elasticsearch.potential_matches_index_name

  def search(
    request: PotentialMatchItemSearch
  ): Future[EsPotentialMatchResponse] = {
    val query = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termQuery("state", EsPotentialMatchState.Unmatched.toString)
      )
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
      .applyOptional(request.bookmark)(
        (builder, bm) =>
          builder.filter(QueryBuilders.rangeQuery("id").lt(bm.value))
      )

    val countFut = count(request)

    val searchSource = new SearchSourceBuilder()
      .query(query)
      .size(request.limit)
      .sort("id", SortOrder.DESC)

    val searchRequest =
      new SearchRequest(indexName)
        .source(searchSource)

    val searchFut = elasticsearchExecutor
      .search(searchRequest)

    for {
      searchResponse <- searchFut
      countResponse <- countFut
    } yield {
      val items = decodeSearchResponse[EsPotentialMatchItem](searchResponse)
      val bookmark = items.lastOption.map(item => {
        Bookmark(SearchScore(), item.id, None)
      })

      EsPotentialMatchResponse(
        items,
        countResponse,
        bookmark
      )
    }
  }

  def count(request: PotentialMatchItemSearch): Future[Long] = {
    val query = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termQuery("state", EsPotentialMatchState.Unmatched.toString)
      )
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

    elasticsearchExecutor
      .count(
        new CountRequest(indexName)
          .source(new SearchSourceBuilder().query(query))
      )
      .map(_.getCount)
  }

  def updateState(
    id: String,
    state: EsPotentialMatchState
  ): Future[Unit] = {
    val updateRequest = new UpdateRequest(indexName, id)
      .doc(Map("state" -> state.getName).asJava, XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

    elasticsearchExecutor.update(updateRequest).map(_ => {})
  }
}

case class PotentialMatchItemSearch(
  scraperType: Option[ScrapeItemType],
  limit: Int,
  bookmark: Option[Bookmark])

abstract class ElasticsearchCrud[Id, T: Codec: ClassTag](
  implicit hasId: HasId.Aux[T, Id],
  executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  protected def indexName: String
  protected def elasticsearchExecutor: ElasticsearchExecutor

  def lookup(id: Id): Future[Option[T]] = {
    elasticsearchExecutor
      .get(
        new GetRequest(indexName, hasId.asString(id))
      )
      .map(response => {
        decodeSourceString[T](response.getSourceAsString)
      })
  }

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
