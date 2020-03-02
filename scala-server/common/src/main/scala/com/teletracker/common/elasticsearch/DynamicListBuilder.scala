package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredUserList
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
  DynamicListReleaseYearRule,
  DynamicListTagRule,
  PersonAssociationType,
  TrackedListRow,
  UserThingTagType
}
import com.teletracker.common.elasticsearch.EsItemTag.TagFormatter
import com.teletracker.common.util.{IdOrSlug, ListFilters, OpenDateRange}
import javax.inject.Inject
import com.teletracker.common.util.Functions._
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class DynamicListBuilder @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  personLookup: PersonLookup,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def getDynamicListCounts(
    userId: String,
    lists: List[StoredUserList]
  ): Future[List[(UUID, Long)]] = {
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
          .map(
            new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
              .source(_)
          )

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
    list: StoredUserList
  ): Future[Long] = {
    val listQuery =
      getDynamicListQuery(userId, list, None, None, None, None)

    val countRequest = new CountRequest(
      teletrackerConfig.elasticsearch.items_index_name
    ).source(listQuery)

    elasticsearchExecutor
      .count(countRequest)
      .map(response => response.getCount)
  }

  def buildDynamicList(
    userId: String,
    list: StoredUserList,
    listFilters: Option[ListFilters],
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long, List[EsPerson])] = {
    val listQuery = getDynamicListQuery(
      userId,
      list,
      listFilters,
      Some(sortMode),
      bookmark,
      Some(limit)
    )

    val peopleToFetch = list.rules.toList.flatMap(_.rules).collect {
      case personRule: DynamicListPersonRule => personRule.personId
    }

    val peopleForRulesFut = if (peopleToFetch.nonEmpty) {
      personLookup.lookupPeople(peopleToFetch.map(IdOrSlug.fromUUID))
    } else {
      Future.successful(Nil)
    }

    val searchRequest = new SearchRequest(
      teletrackerConfig.elasticsearch.items_index_name
    ).source(listQuery)

    val itemsFut = elasticsearchExecutor
      .search(searchRequest)
      .map(searchResponseToItems)
      .map(applyNextBookmark(_, bookmark, sortMode, isDynamic = true))

    val countFut = getDynamicListItemCount(userId, list)

    for {
      items <- itemsFut
      count <- countFut
      people <- peopleForRulesFut
    } yield {
      (
        items,
        count,
        // TODO: gnarly hack to appease the frontend... think about this later
        // This is a preemptive fetch to get some light details on a person, but returning credits makes
        // future full fetches difficult because cast_credits here would overwrite frontend state...
        people.map(_.copy(cast_credits = None, crew_credits = None))
      )
    }
  }

  private def getDynamicListQuery(
    userId: String,
    dynamicList: StoredUserList,
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

    val releaseYearRules = rules.rules.collect {
      case releaseYearRule: DynamicListReleaseYearRule => releaseYearRule
    }

    require(
      releaseYearRules.size < 2,
      "Cannot have multiple release year rules!"
    )

    val releaseYearRule =
      releaseYearRules.headOption.map(
        rule => OpenDateRange.forYearRange(rule.minimum, rule.maximum)
      )

    val genreIdsToUse = listFilters.flatMap(_.genres).orElse {
      Some(
        rules.rules
          .collect {
            case genreRule: DynamicListGenreRule => genreRule
          }
          .map(_.genreId)
      ).filter(_.nonEmpty)
    }

    val itemTypesToUse = listFilters.flatMap(_.itemTypes).orElse {
      Some(
        rules.rules
          .collect {
            case itemTypeRule: DynamicListItemTypeRule => itemTypeRule
          }
          .map(_.itemType)
          .toSet
      ).filter(_.nonEmpty)
    }

    val networkIdsToUse = listFilters.flatMap(_.networks).orElse {
      Some(
        rules.rules
          .collect {
            case networkRule: DynamicListNetworkRule => networkRule
          }
          .map(_.networkId)
          .toSet
      ).filter(_.nonEmpty)
    }

    val peopleIdsToUse = listFilters
      .flatMap(_.personIdentifiers)
      .map(ids => personIdentifiersToSearch(ids.toList))
      .orElse {
        Some(
          rules.rules.collect {
            case personRule: DynamicListPersonRule => personRule
          }.distinct
        ).filter(_.nonEmpty).map(personRulesToSearch)
      }

    val excludeWatchedItems = dynamicList.options.exists(_.removeWatchedItems)

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
      .applyOptional(peopleIdsToUse)(
        peopleCreditSearchQuery
      )
      .applyOptional(itemTypesToUse.filter(_.nonEmpty))(
        itemTypesFilter
      )
      .applyOptional(genreIdsToUse.map(_.toSet).filter(_.nonEmpty))(
        genreIdsFilter
      )
      .applyOptional(
        networkIdsToUse.filter(_.nonEmpty)
      )(
        availabilityByNetworkIdsOr
      )
      .applyOptional(releaseYearRule.filter(_.isFinite))(openDateRangeFilter)
      .applyIf(excludeWatchedItems)(excludeWatchedItemsFilter(userId, _))
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

  private def personIdentifiersToSearch(personIds: List[String]) = {
    val creditSearches = personIds.flatMap(id => {
      List(
        PersonCreditSearch(
          IdOrSlug(id),
          PersonAssociationType.Cast
        ),
        PersonCreditSearch(
          IdOrSlug(id),
          PersonAssociationType.Crew
        )
      )
    })

    PeopleCreditSearch(creditSearches, BinaryOperator.Or)
  }

  private def excludeWatchedItemsFilter(
    userId: String,
    boolQueryBuilder: BoolQueryBuilder
  ) = {
    boolQueryBuilder.mustNot(
      QueryBuilders.termQuery(
        "tags.tag",
        TagFormatter.format(userId, UserThingTagType.Watched)
      )
    )
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
