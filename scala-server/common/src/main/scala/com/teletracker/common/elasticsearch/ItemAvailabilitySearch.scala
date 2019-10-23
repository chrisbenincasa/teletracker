package com.teletracker.common.elasticsearch

import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import com.teletracker.common.util.Functions._
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{
  FieldSortBuilder,
  NestedSortBuilder,
  SortMode,
  SortOrder
}
import scala.concurrent.{ExecutionContext, Future}

class ItemAvailabilitySearch @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def findNew(
    daysOut: Int,
    networkIds: Option[Set[Int]]
  ): Future[ElasticsearchItemsResponse] = {
    val startRange = QueryBuilders.nestedQuery(
      "availability",
      QueryBuilders
        .rangeQuery("availability.start_date")
        .lte("now")
        .gte(s"now-${daysOut}d"),
      ScoreMode.Avg
    )

    val boolQuery = QueryBuilders
      .boolQuery()
      .filter(startRange)
      .through(applyAvailabilityNetworkFilters(_, networkIds))

    val searchSource = new SearchSourceBuilder()
      .query(boolQuery)
      .through(applySorts(_, "availability.start_date", SortOrder.DESC))

    elasticsearchExecutor
      .search(new SearchRequest().source(searchSource))
      .map(searchResponseToItems)
  }

  def findUpcoming(
    daysOut: Int,
    networkIds: Option[Set[Int]]
  ): Future[ElasticsearchItemsResponse] = {
    val startRange = QueryBuilders.nestedQuery(
      "availability",
      QueryBuilders
        .rangeQuery("availability.start_date")
        .gt("now")
        .lte(s"now+${daysOut}d"),
      ScoreMode.Avg
    )

    val boolQuery = QueryBuilders
      .boolQuery()
      .filter(startRange)
      .through(applyAvailabilityNetworkFilters(_, networkIds))

    val searchSource = new SearchSourceBuilder()
      .query(boolQuery)
      .through(applySorts(_, "availability.start_date", SortOrder.ASC))

    elasticsearchExecutor
      .search(new SearchRequest().source(searchSource))
      .map(searchResponseToItems)
  }

  def findExpiring(
    daysOut: Int,
    networkIds: Option[Set[Int]]
  ): Future[ElasticsearchItemsResponse] = {
    val startRange = QueryBuilders.nestedQuery(
      "availability",
      QueryBuilders
        .rangeQuery("availability.end_date")
        .gt("now")
        .lte(s"now+${daysOut}d"),
      ScoreMode.Avg
    )

    val boolQuery = QueryBuilders
      .boolQuery()
      .filter(startRange)
      .through(applyAvailabilityNetworkFilters(_, networkIds))

    val searchSource = new SearchSourceBuilder()
      .query(boolQuery)
      .through(applySorts(_, "availability.end_date", SortOrder.ASC))

    elasticsearchExecutor
      .search(new SearchRequest().source(searchSource))
      .map(searchResponseToItems)
  }

  private def applySorts(
    sourceBuilder: SearchSourceBuilder,
    availabilitySortPath: String,
    dateOrder: SortOrder
  ) = {
    sourceBuilder
      .sort(
        new FieldSortBuilder(availabilitySortPath)
          .setNestedSort(new NestedSortBuilder("availability"))
          .order(dateOrder)
          .sortMode(SortMode.AVG)
      )
      .sort(new FieldSortBuilder("popularity").order(SortOrder.DESC))
  }

  private def applyAvailabilityNetworkFilters(
    boolQuery: BoolQueryBuilder,
    networkIds: Option[Set[Int]]
  ) = {
    boolQuery.applyOptional(networkIds.filter(_.nonEmpty))(
      (builder, ids) =>
        ids
          .foldLeft(builder)(
            (b, id) =>
              b.should(
                QueryBuilders.nestedQuery(
                  "availability",
                  QueryBuilders.termQuery("availability.network_id", id),
                  ScoreMode.Avg
                )
              )
          )
          .minimumShouldMatch(1)
    )
  }
}
