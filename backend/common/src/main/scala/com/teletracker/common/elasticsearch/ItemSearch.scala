package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db._
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.{EsItem, ItemSearchParams}
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders
import org.elasticsearch.index.query.{
  MultiMatchQueryBuilder,
  Operator,
  QueryBuilders
}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

class ItemSearch @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def fullTextSearch(
    params: ItemSearchParams
  ): Future[ElasticsearchItemsResponse] = {
    require(params.titleSearch.isDefined)

    if (params.bookmark.isDefined) {
      require(params.bookmark.get.sortType == SortMode.SearchScoreType)
    }

    val textQuery = params.titleSearch.get

    def makeMultiMatchQuery(
      query: String,
      boost: Float = 1.0f
    ) = {
      // TODO: Use this method when we have ES 7.5 available
//      QueryBuilders
//        .multiMatchQuery(
//          query,
//          "title^2",
//          "title._2gram",
//          "title._3gram",
//          "original_title",
//          "original_title._2gram",
//          "original_title._3gram"
//        )
//        .`type`(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
//        .fuzziness(5)
//        .operator(Operator.AND)
      QueryBuilders
        .boolQuery()
        .should(
          QueryBuilders
            .multiMatchQuery(
              query,
              "title",
              "title._2gram",
              "title._3gram"
            )
            .`type`(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
            .fuzziness(5)
            .operator(Operator.AND)
            .boost(2.0f)
        )
        .should(
          QueryBuilders
            .multiMatchQuery(
              query,
              "original_title",
              "original_title._2gram",
              "original_title._3gram"
            )
            .`type`(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
            .fuzziness(5)
            .operator(Operator.AND)
        )
        .minimumShouldMatch(1)
    }

    // TODO: Support all of the filters that regular search does
    val searchQuery = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .boolQuery()
          .should(makeMultiMatchQuery(textQuery, 1.2f))
          .applyOptional(FullTextSynonyms.replaceWithSynonym(textQuery))(
            (builder, synonymQuery) =>
              builder.should(makeMultiMatchQuery(synonymQuery))
          )
          .minimumShouldMatch(1)
      )
      .applyOptional(params.genres.filter(_.nonEmpty))(genresFilter)
      .applyOptional(params.networks.filter(_.nonEmpty))(
        availabilityByNetworksOr
      )
      .applyOptional(params.releaseYear.filter(_.isFinite))(
        openDateRangeFilter
      )
      .applyOptional(
        params.peopleCredits
          .filter(_.people.nonEmpty)
      )(
        peopleCreditSearchQuery
      )
      .applyOptional(params.tagFilters)((builder, tags) => {
        tags.foldLeft(builder)(itemTagFilter)
      })
      .through(posterImageFilter)
      .through(removeAdultItems)
      .applyOptional(params.itemTypes.filter(_.nonEmpty))(
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
      .size(params.limit)
      .applyOptional(params.bookmark)((builder, bookmark) => {
        builder.from(bookmark.value.toInt)
      })

    println(searchSource)

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
        .source(
          searchSource
        )

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToItems)
      .map(response => {
        val lastOffset = params.bookmark.map(_.value.toInt).getOrElse(0)
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
    params: ItemSearchParams
  ): Future[ElasticsearchItemsResponse] = {
    if (params.limit <= 0) {
      Future.successful(ElasticsearchItemsResponse.empty)
    } else {
      val actualSortMode =
        params.bookmark.map(_.sortMode).getOrElse(params.sortMode)

      val searchSourceBuilder = new SearchSourceBuilder()
        .query(buildSearchRequest(params))
        .size(params.limit)
        .applyOptional(makeDefaultSort(actualSortMode))(_.sort(_))
        .sort(
          new FieldSortBuilder("id").order(SortOrder.ASC)
        )

      elasticsearchExecutor
        .search(
          new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
            .source(searchSourceBuilder)
        )
        .map(searchResponseToItems)
        .map(applyNextBookmark(_, params.bookmark, params.sortMode))
    }
  }

  def countItems(params: ItemSearchParams): Future[Long] = {
    val searchSourceBuilder = new SearchSourceBuilder()
      .query(buildSearchRequest(params))

    val request =
      new CountRequest(teletrackerConfig.elasticsearch.items_index_name)
        .source(searchSourceBuilder)

    elasticsearchExecutor
      .count(
        request
      )
      .map(response => response.getCount)
  }

  private def buildSearchRequest(params: ItemSearchParams) = {
    QueryBuilders
      .boolQuery()
      .applyOptional(params.genres.filter(_.nonEmpty))(genresFilter)
      .through(removeAdultItems)
      .through(posterImageFilter)
      .applyOptional(params.networks.filter(_.nonEmpty))(
        availabilityByNetworksOr
      )
      .applyOptional(params.releaseYear.filter(_.isFinite))(
        openDateRangeFilter
      )
      .applyOptional(params.itemTypes.filter(_.nonEmpty))(itemTypesFilter)
      .applyOptional(params.bookmark)(applyBookmark(_, _, list = None))
      .applyOptional(
        params.peopleCredits
          .filter(_.people.nonEmpty)
      )(
        peopleCreditSearchQuery
      )
      .applyOptional(params.tagFilters)(
        (builder, tags) => tags.foldLeft(builder)(itemTagFilter)
      )
      .applyOptional(params.imdbRating)(
        openRatingRangeFilter(_, ExternalSource.Imdb, _)
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

        case Rating(desc, source) =>
          item.ratingsGrouped
            .get(source)
            .flatMap(_.weighted_average)
            .getOrElse(if (desc) Double.MinValue else Double.MaxValue)
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
