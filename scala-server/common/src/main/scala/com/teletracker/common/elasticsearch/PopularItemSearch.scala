package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.{Genre, Network, ThingType}
import com.teletracker.common.db.{Bookmark, Popularity, Recent, SortMode}
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import scala.concurrent.{ExecutionContext, Future}

class PopularItemSearch @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def getPopularItems(
    genre: Option[Genre],
    networks: Set[Network],
    itemTypes: Option[Set[ThingType]],
    limit: Int,
    bookmark: Option[Bookmark]
  ): Future[ElasticsearchItemsResponse] = {
    val validBookmark = bookmark.collect {
      case bm @ Bookmark(SortMode.PopularityType, true, _, _) =>
        bm
    }

    val query = QueryBuilders
      .boolQuery()
      .applyOptional(genre)((builder, g) => {
        builder.filter(
          QueryBuilders.nestedQuery(
            "genres",
            QueryBuilders.termQuery("genres.id", g.id.get),
            ScoreMode.Avg
          )
        )
      })
      .through(removeAdultItems)
      .applyIf(networks.nonEmpty)(
        builder => networks.foldLeft(builder)(availabilityByNetwork)
      )
      .applyOptional(itemTypes.filter(_.nonEmpty))(
        (builder, types) => types.foldLeft(builder)(itemTypeFilter)
      )
      .applyOptional(validBookmark)(applyBookmark)

    val searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(limit)
      .applyOptional(makeSort(Popularity()))(_.sort(_))
      .sort(
        new FieldSortBuilder("id").order(SortOrder.ASC)
      )

    elasticsearchExecutor
      .search(
        new SearchRequest().source(searchSourceBuilder)
      )
      .map(searchResponseToItems)
      .map(response => {
        val nextBookmark = response.items.lastOption
          .map(
            thing => {
              val refinement = bookmark
                .filter(_.value == thing.popularity.getOrElse(0.0).toString)
                .map(_ => thing.id.toString)

              Bookmark(
                Popularity(),
                thing.popularity.getOrElse(0.0).toString,
                refinement
              )
            }
          )

        response.withBookmark(nextBookmark)
      })
  }

  private def availabilityByNetwork(
    builder: BoolQueryBuilder,
    network: Network
  ) = {
    builder.filter(
      QueryBuilders.nestedQuery(
        "availability",
        QueryBuilders.termQuery("availability.network_id", network.id.get),
        ScoreMode.Avg
      )
    )
  }
}
