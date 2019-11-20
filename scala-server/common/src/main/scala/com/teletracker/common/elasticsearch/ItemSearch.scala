package com.teletracker.common.elasticsearch

import com.teletracker.common.db.access.SearchOptions
import com.teletracker.common.db.model.{
  Genre,
  Network,
  PersonAssociationType,
  ThingType
}
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
import com.teletracker.common.util.{IdOrSlug, OpenDateRange, OpenRange}
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders
import org.elasticsearch.index.query.{
  BoolQueryBuilder,
  MultiMatchQueryBuilder,
  Operator,
  QueryBuilders,
  TermQueryBuilder
}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class ItemSearch @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def fullTextSearch(
    textQuery: String,
    searchOptions: SearchOptions
  ): Future[ElasticsearchItemsResponse] = {
    if (searchOptions.bookmark.isDefined) {
      require(searchOptions.bookmark.get.sortType == SortMode.SearchScoreType)
    }

    // TODO: Support all of the filters that regular search does
    val searchQuery = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .multiMatchQuery(
            textQuery,
            "title",
            "title._2gram",
            "title._3gram"
          )
          .`type`(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
          .operator(Operator.AND)
      )
      .through(posterImageFilter)
      .through(removeAdultItems)
      .applyOptional(searchOptions.thingTypeFilter.filter(_.nonEmpty))(
        (builder, types) => types.foldLeft(builder)(itemTypeFilter)
      )

    val query = QueryBuilders.functionScoreQuery(
      searchQuery,
      ScoreFunctionBuilders
        .fieldValueFactorFunction("popularity")
        .factor(1.2f)
        .missing(0.8)
        .modifier(FieldValueFactorFunction.Modifier.SQRT)
    )

    val searchSource = new SearchSourceBuilder()
      .query(query)
      .size(searchOptions.limit)
      .applyOptional(searchOptions.bookmark)((builder, bookmark) => {
        builder.from(bookmark.value.toInt)
      })

    val search = new SearchRequest("items")
      .source(
        searchSource
      )

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToItems)
      .map(response => {
        val lastOffset = searchOptions.bookmark.map(_.value.toInt).getOrElse(0)
        response.withBookmark(
          if (response.items.isEmpty) None
          else
            Some(
              Bookmark(
                SearchScore(),
                (response.items.size + lastOffset).toString,
                None
              )
            )
        )
      })
  }

  def searchItems(
    genres: Option[Set[Genre]],
    networks: Option[Set[Network]],
    itemTypes: Option[Set[ThingType]],
    sortMode: SortMode,
    limit: Int,
    bookmark: Option[Bookmark],
    releaseYear: Option[OpenDateRange],
    peopleCreditSearch: Option[PeopleCreditSearch]
  ): Future[ElasticsearchItemsResponse] = {
    val actualSortMode = bookmark.map(_.sortMode).getOrElse(sortMode)

    val query = QueryBuilders
      .boolQuery()
      .applyOptional(genres.filter(_.nonEmpty))(genresFilter)
      .through(removeAdultItems)
      .through(posterImageFilter)
      .applyOptional(networks.filter(_.nonEmpty))(availabilityByNetworksOr)
      .applyOptional(releaseYear.filter(_.isFinite))(openDateRangeFilter)
      .applyOptional(itemTypes.filter(_.nonEmpty))(itemTypesFilter)
      .applyOptional(bookmark)(applyBookmark(_, _, list = None))
      .applyOptional(peopleCreditSearch.filter(_.people.nonEmpty))(
        peopleCreditSearchQuery
      )

    val searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(limit)
      .applyOptional(makeDefaultSort(actualSortMode))(_.sort(_))
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
