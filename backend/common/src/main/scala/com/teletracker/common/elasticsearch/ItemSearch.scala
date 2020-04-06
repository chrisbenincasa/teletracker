package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db._
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.{
  ClosedNumericRange,
  OpenDateRange,
  OpenNumericRange
}
import javax.inject.Inject
import org.elasticsearch.action.search.SearchRequest
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
    textQuery: String,
    searchOptions: SearchOptions
  ): Future[ElasticsearchItemsResponse] = {
    if (searchOptions.bookmark.isDefined) {
      require(searchOptions.bookmark.get.sortType == SortMode.SearchScoreType)
    }

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
//        .boost(boost)

//      QueryBuilders
//        .multiMatchQuery(
//          query,
//          "title",
//          "title._2gram",
//          "title._3gram",
//          "original_title",
//          "original_title._2gram",
//          "original_title._3gram",
//          "alternative_titles",
//          "alternative_titles._2gram",
//          "alternative_titles._3gram"
//        )
//        .`type`(MultiMatchQueryBuilder.Type.PHRASE_PREFIX)
//        .maxExpansions(50)
//        .operator(Operator.OR)
//        .boost(boost)
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
      .applyOptional(searchOptions.genres.filter(_.nonEmpty))(genresFilter)
      .applyOptional(searchOptions.networks.filter(_.nonEmpty))(
        availabilityByNetworksOr
      )
      .applyOptional(searchOptions.releaseYear.filter(_.isFinite))(
        openDateRangeFilter
      )
      .applyOptional(
        searchOptions.peopleCreditSearch.filter(_.people.nonEmpty)
      )(
        peopleCreditSearchQuery
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
    genres: Option[Set[StoredGenre]],
    networks: Option[Set[StoredNetwork]],
    itemTypes: Option[Set[ItemType]],
    sortMode: SortMode,
    limit: Int,
    bookmark: Option[Bookmark],
    releaseYear: Option[OpenDateRange],
    peopleCreditSearch: Option[PeopleCreditSearch],
    imdbRatingRange: Option[ClosedNumericRange[Double]]
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
      .applyOptional(imdbRatingRange)(
        openRatingRangeFilter(_, ExternalSource.Imdb, _)
      )

    val searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(limit)
      .applyOptional(makeDefaultSort(actualSortMode))(_.sort(_))
      .sort(
        new FieldSortBuilder("id").order(SortOrder.ASC)
      )

//    println(s"Search: ${searchSourceBuilder}")

    elasticsearchExecutor
      .search(
        new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
          .source(searchSourceBuilder)
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
