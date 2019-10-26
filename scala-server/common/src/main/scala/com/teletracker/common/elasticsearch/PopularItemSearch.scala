package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.{Genre, Network, ThingType}
import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

class PopularItemSearch @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def getPopularItems(
    genres: Option[Set[Genre]],
    networks: Option[Set[Network]],
    itemTypes: Option[Set[ThingType]],
    sortMode: SortMode,
    limit: Int,
    bookmark: Option[Bookmark]
  ): Future[ElasticsearchItemsResponse] = {
    val actualSortMode = bookmark.map(_.sortMode).getOrElse(sortMode)

    val query = QueryBuilders
      .boolQuery()
      .applyOptional(genres.filter(_.nonEmpty))(genresFilter)
      .through(removeAdultItems)
      .through(posterImageFilter)
      .applyOptional(networks.filter(_.nonEmpty))(
        (builder, networks) => networks.foldLeft(builder)(availabilityByNetwork)
      )
      .applyOptional(itemTypes.filter(_.nonEmpty))(itemTypesFilter)
      .applyOptional(bookmark)(applyBookmark)

    val searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(limit)
      .applyOptional(makeSort(actualSortMode))(_.sort(_))
      .sort(
        new FieldSortBuilder("id").order(SortOrder.ASC)
      )

    elasticsearchExecutor
      .search(
        new SearchRequest().source(searchSourceBuilder)
      )
      .map(searchResponseToItems)
      .map(applyNextBookmark(_, bookmark, sortMode))
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

  private def applyNextBookmark(
    response: ElasticsearchItemsResponse,
    previousBookmark: Option[Bookmark],
    sortMode: SortMode
  ): ElasticsearchItemsResponse = {
    @tailrec
    def getValueForBookmark(
      item: EsItem,
      actualSortMode: SortMode
    ): String = {
      actualSortMode match {
        case SearchScore(_) => throw new IllegalStateException("")

        case Popularity(_) => item.popularity.getOrElse(0.0).toString

        case Recent(desc) =>
          item.release_date
            .getOrElse(if (desc) LocalDate.MIN else LocalDate.MAX)
            .toString

        case AddedTime(desc) =>
          getValueForBookmark(item, Recent(desc))

        case DefaultForListType(_) =>
          getValueForBookmark(item, Popularity())
      }
    }

    val nextBookmark = response.items.lastOption
      .map(
        item => {
          val value = getValueForBookmark(item, sortMode)

          val refinement = previousBookmark
            .filter(_.value == value)
            .map(_ => item.id.toString)

          Bookmark(
            sortMode,
            value,
            refinement
          )
        }
      )

    response.withBookmark(nextBookmark)
  }
}
