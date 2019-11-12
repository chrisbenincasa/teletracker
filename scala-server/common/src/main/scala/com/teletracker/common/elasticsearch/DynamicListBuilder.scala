package com.teletracker.common.elasticsearch

import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import com.teletracker.common.db.model.{
  DynamicListGenreRule,
  DynamicListItemTypeRule,
  DynamicListNetworkRule,
  DynamicListPersonRule,
  DynamicListTagRule,
  PersonAssociationType,
  TrackedListRow
}
import com.teletracker.common.elasticsearch.EsItemTag.TagFormatter
import com.teletracker.common.util.{IdOrSlug, ListFilters}
import javax.inject.Inject
import com.teletracker.common.util.Functions._
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class DynamicListBuilder @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def getDynamicListCounts(
    userId: String,
    lists: List[TrackedListRow]
  ): Future[List[(Int, Long)]] = {
    Promise
      .fromTry(Try {
        require(lists.nonEmpty)
        require(lists.forall(_.isDynamic))
      })
      .future
      .flatMap(_ => {
        val queries = lists
          .map(getDynamicListQuery(userId, _, None, None, None, None))
          .map(_.fetchSource(false))
          .map(new SearchRequest().source(_))

        val mSearchRequest = new MultiSearchRequest()

        queries.foreach(mSearchRequest.add)

        elasticsearchExecutor
          .multiSearch(mSearchRequest)
          .map(searchResponse => {
            searchResponse.getResponses
              .zip(lists)
              .map {
                case (response, list) => {
                  list.id -> response.getResponse.getHits.getTotalHits.value
                }
              }
              .toList
          })
      })
  }

  def getDynamicListItemCount(
    userId: String,
    list: TrackedListRow
  ): Future[Long] = {
    val listQuery =
      getDynamicListQuery(userId, list, None, None, None, None)

    val countRequest = new CountRequest("items").source(listQuery)

    elasticsearchExecutor
      .count(countRequest)
      .map(response => response.getCount)
  }

  def buildDynamicList(
    userId: String,
    list: TrackedListRow,
    listFilters: Option[ListFilters],
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long)] = {
    val listQuery = getDynamicListQuery(
      userId,
      list,
      listFilters,
      Some(sortMode),
      bookmark,
      Some(limit)
    )

    val searchRequest = new SearchRequest("items").source(listQuery)

    val itemsFut = elasticsearchExecutor
      .search(searchRequest)
      .map(searchResponseToItems)
      .map(applyNextBookmark(_, bookmark, sortMode, isDynamic = true))

    val countFut = getDynamicListItemCount(userId, list)

    for {
      items <- itemsFut
      count <- countFut
    } yield {
      items -> count
    }
  }

  private def getDynamicListQuery(
    userId: String,
    dynamicList: TrackedListRow,
    listFilters: Option[ListFilters],
    sortMode: Option[SortMode],
    bookmark: Option[Bookmark],
    limit: Option[Int]
  ): SearchSourceBuilder = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    val tagRules = rules.rules.collect {
      case tagRule: DynamicListTagRule => tagRule
    }

    val personRules = rules.rules.collect {
      case personRule: DynamicListPersonRule => personRule
    }

    val genreRules = rules.rules.collect {
      case genreRule: DynamicListGenreRule => genreRule
    }

    val itemTypeRules = rules.rules.collect {
      case itemTypeRule: DynamicListItemTypeRule => itemTypeRule
    }

    val clientSpecifiedGenres = listFilters.flatMap(_.genres)

    val genreIdsToUse = clientSpecifiedGenres.orElse {
      Some(genreRules.map(_.genreId)).filter(_.nonEmpty)
    }

    val clientSpecifiedItemTypes =
      listFilters.flatMap(_.itemTypes).filter(_.nonEmpty)

    val itemTypesToUse = clientSpecifiedItemTypes.orElse {
      Some(itemTypeRules.map(_.itemType).toSet).filter(_.nonEmpty)
    }

    val networkRules = rules.rules.collect {
      case networkRule: DynamicListNetworkRule => networkRule
    }

    val sourceBuilder = new SearchSourceBuilder()
    val baseBoolQuery = QueryBuilders.boolQuery()

    baseBoolQuery
      .applyIf(tagRules.nonEmpty)(
        builder =>
          tagRules.foldLeft(builder)((query, rule) => {
            query.filter(
              QueryBuilders
                .termQuery(
                  "tags.tag",
                  TagFormatter.format(userId, rule.tagType)
                )
            )
          })
      )
      .applyIf(personRules.nonEmpty)(
        peopleCreditSearchQuery(_, personRulesToSearch(personRules))
      )
      .applyOptional(itemTypesToUse.filter(_.nonEmpty))(
        itemTypesFilter
      )
      .applyOptional(genreIdsToUse.map(_.toSet))(genreIdsFilter)
      .applyOptional(
        Some(networkRules.map(_.networkId)).filter(_.nonEmpty).map(_.toSet)
      )(
        availabilityByNetworkIdsOr
      )
      .applyOptional(bookmark)(applyBookmark(_, _, Some(dynamicList)))

    val defaultSort =
      dynamicList.rules.flatMap(_.sort).map(_.sort).map(SortMode.fromString)
    val sortToUse =
      bookmark.map(_.sortMode).orElse(sortMode).orElse(defaultSort)

    sourceBuilder
      .query(baseBoolQuery)
      .applyOptional(sortToUse)(
        (builder, sort) => {
          builder
            .applyOptional(makeDefaultSort(sort))(_.sort(_))
            .sort(
              new FieldSortBuilder("id").order(SortOrder.ASC)
            )
        }
      )
      .applyOptional(limit)(_.size(_))
  }

  private def personRulesToSearch(personRules: List[DynamicListPersonRule]) = {
    val creditSearches = personRules.flatMap(rule => {
      List(
        if (rule.associationType.isEmpty || rule.associationType
              .contains(PersonAssociationType.Cast))
          Some(
            PersonCreditSearch(
              IdOrSlug.fromUUID(rule.personId),
              PersonAssociationType.Cast
            )
          )
        else None,
        if (rule.associationType.isEmpty || rule.associationType
              .contains(PersonAssociationType.Crew))
          Some(
            PersonCreditSearch(
              IdOrSlug.fromUUID(rule.personId),
              PersonAssociationType.Crew
            )
          )
        else None
      ).flatten
    })

    PeopleCreditSearch(creditSearches, BinaryOperator.Or)
  }

  private def applyNextBookmark(
    response: ElasticsearchItemsResponse,
    previousBookmark: Option[Bookmark],
    sortMode: SortMode,
    isDynamic: Boolean
  ): ElasticsearchItemsResponse = {
    @tailrec
    def getValueForBookmark(
      item: EsItem,
      actualSortMode: SortMode
    ): String = {
      actualSortMode match {
        case SearchScore(_) => throw new IllegalStateException("")
        case Popularity(_)  => item.popularity.getOrElse(0.0).toString
        case Recent(desc) =>
          item.release_date
            .getOrElse(if (desc) LocalDate.MIN else LocalDate.MAX)
            .toString
        case AddedTime(desc) =>
          // TODO implement correctly for direct lists
          getValueForBookmark(item, Recent(desc))

        case default @ DefaultForListType(_) =>
          getValueForBookmark(item, default.get(isDynamic))
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
