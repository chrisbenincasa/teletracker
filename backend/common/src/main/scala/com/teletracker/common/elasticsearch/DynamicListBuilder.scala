package com.teletracker.common.elasticsearch

import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredUserList}
import com.teletracker.common.db.model.{
  DynamicListPersonRule,
  PersonAssociationType,
  UserThingTagType
}
import com.teletracker.common.db._
import com.teletracker.common.elasticsearch.model.EsItemTag.TagFormatter
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsPerson,
  ItemSearchParams,
  TagFilter
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util._
import javax.inject.Inject
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import java.time.LocalDate
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class DynamicListBuilder @Inject()(
  personLookup: PersonLookup,
  genreCache: GenreCache,
  networkCache: NetworkCache,
  itemSearch: ItemSearch
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
        Future
          .sequence(
            lists.map(
              list =>
                getDynamicListQuery(userId, list, None, None, None, None)
                  .map(list -> _)
            )
          )
          .flatMap(listsAndQueries => {
            AsyncStream
              .fromSeq(listsAndQueries)
              .grouped(5)
              .mapF(group => {
                Future.sequence(group.map {
                  case (list, params) =>
                    itemSearch.countItems(params).map(count => list.id -> count)
                })
              })
              .foldLeft(List.empty[(UUID, Long)])(_ ++ _)
          })
      })
  }

  def getDynamicListItemCount(
    userId: String,
    list: StoredUserList
  ): Future[Long] = {
    getDynamicListQuery(userId, list, None, None, None, None).flatMap(
      itemSearch.countItems
    )
  }

  def buildDynamicList(
    userId: String,
    list: StoredUserList,
    ruleOverrides: Option[ItemSearchParams],
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long, List[EsPerson])] = {
    require(list.isDynamic)
    require(list.rules.isDefined)

    val listQuery = getDynamicListQuery(
      userId,
      list,
      ruleOverrides,
      Some(sortMode),
      bookmark,
      Some(limit)
    )

    val peopleToFetch = list.rules.toList.flatMap(_.personRules).map(_.personId)
    val peopleForRulesFut = if (peopleToFetch.nonEmpty) {
      personLookup.lookupPeople(peopleToFetch.map(IdOrSlug.fromUUID))
    } else {
      Future.successful(Nil)
    }

    val itemsFut = listQuery.flatMap(itemSearch.searchItems)
//    val countFut = listQuery.flatMap(itemSearch.countItems)

    for {
      items <- itemsFut
//      count <- countFut
      people <- peopleForRulesFut
    } yield {
      (
        items,
        items.totalHits,
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
    ruleOverrides: Option[ItemSearchParams],
    sortMode: Option[SortMode],
    bookmark: Option[Bookmark],
    limit: Option[Int]
  ): Future[ItemSearchParams] = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val genresFut = genreCache.getById()
    val networksFut = networkCache.getAllNetworksById()

    val rules = dynamicList.rules.get
    val genreRules = rules.genreRules
    val itemTypeRules = rules.itemTypeRules
    val networkRules = rules.networkRules
    val tagRules = rules.tagRules
    val excludeWatchedItems = dynamicList.options.exists(_.removeWatchedItems)
    val releaseYearRule = rules.releaseYearRules
      .through(ryRules => {
        require(
          ryRules.size < 2,
          "Cannot have multiple release year rules!"
        )
        ryRules
      })
      .headOption

    val peopleRules = rules.personRules

    val personSearch = if (peopleRules.isEmpty) {
      None
    } else {
      val searches = peopleRules.flatMap(rule => {
        rule.associationType match {
          case Some(value) =>
            List(PersonCreditSearch(IdOrSlug.fromUUID(rule.personId), value))
          case None =>
            PersonAssociationType
              .values()
              .map(t => PersonCreditSearch(IdOrSlug.fromUUID(rule.personId), t))
              .toList
        }
      })

      Some(
        PeopleCreditSearch(
          people = searches,
          BinaryOperator.Or
        )
      )
    }

    val defaultSort =
      ruleOverrides
        .map(_.sortMode)
        .orElse(
          dynamicList.rules.flatMap(_.sort).map(_.sort).map(SortMode.fromString)
        )
    val sortToUse =
      bookmark.map(_.sortMode).orElse(sortMode).orElse(defaultSort)

    for {
      genres <- genresFut
      networks <- networksFut
    } yield {
      val removeWatchedItemFilter =
        if (excludeWatchedItems) {
          List(
            TagFilter(
              TagFormatter.format(userId, UserThingTagType.Watched),
              mustHave = false
            )
          )
        } else {
          Nil
        }

      val tagFilters = tagRules.map(
        rule =>
          TagFilter(TagFormatter.format(userId, rule.tagType), mustHave = true)
      ) ++ removeWatchedItemFilter

      val genreFilters = ruleOverrides match {
        case Some(value)                => value.genres
        case None if genreRules.isEmpty => None
        case None =>
          Some(genreRules.flatMap(rule => genres.get(rule.genreId)).toSet)
      }

      val networkFilters = ruleOverrides match {
        case Some(value)                  => value.networks
        case None if networkRules.isEmpty => None
        case None =>
          Some(
            networkRules.flatMap(rule => networks.get(rule.networkId)).toSet
          )
      }

      val itemTypeFilters = ruleOverrides match {
        case Some(value)                   => value.itemTypes
        case None if itemTypeRules.isEmpty => None
        case None                          => Some(itemTypeRules.map(_.itemType).toSet)
      }

      val releaseYearFilter = ruleOverrides match {
        case Some(value)                     => value.releaseYear
        case None if releaseYearRule.isEmpty => None
        case None                            => releaseYearRule.map(_.range)
      }

      ItemSearchParams(
        genres = genreFilters,
        networks = networkFilters,
        itemTypes = itemTypeFilters,
        releaseYear = releaseYearFilter,
        peopleCredits = personSearch,
        imdbRating = ruleOverrides.flatMap(_.imdbRating), // TODO: Support
        tagFilters = if (tagFilters.nonEmpty) Some(tagFilters) else None,
        titleSearch = None,
        sortMode = sortToUse.getOrElse(Popularity()),
        limit = limit.getOrElse(20),
        bookmark = bookmark
      )
    }
//
//    val genreIdsToUse = listFilters.flatMap(_.genres).orElse {
//      Some(
//        rules.rules
//          .collect {
//            case genreRule: DynamicListGenreRule => genreRule
//          }
//          .map(_.genreId)
//      ).filter(_.nonEmpty)
//    }
//
//    val itemTypesToUse = listFilters.flatMap(_.itemTypes).orElse {
//      Some(
//        rules.rules
//          .collect {
//            case itemTypeRule: DynamicListItemTypeRule => itemTypeRule
//          }
//          .map(_.itemType)
//          .toSet
//      ).filter(_.nonEmpty)
//    }
//
//    val networkIdsToUse = listFilters.flatMap(_.networks).orElse {
//      Some(
//        rules.rules
//          .collect {
//            case networkRule: DynamicListNetworkRule => networkRule
//          }
//          .map(_.networkId)
//          .toSet
//      ).filter(_.nonEmpty)
//    }
//
//    val peopleIdsToUse = listFilters
//      .flatMap(_.personIdentifiers)
//      .map(ids => personIdentifiersToSearch(ids.toList))
//      .orElse {
//        Some(
//          rules.rules.collect {
//            case personRule: DynamicListPersonRule => personRule
//          }.distinct
//        ).filter(_.nonEmpty).map(personRulesToSearch)
//      }
//
//    val excludeWatchedItems = dynamicList.options.exists(_.removeWatchedItems)
//
//    val sourceBuilder = new SearchSourceBuilder()
//    val baseBoolQuery = QueryBuilders.boolQuery()
//
//    baseBoolQuery
//      .applyIf(tagRules.nonEmpty)(
//        builder =>
//          tagRules.foldLeft(builder)((query, rule) => {
//            query.filter(
//              QueryBuilders
//                .termQuery(
//                  "tags.tag",
//                  TagFormatter.format(userId, rule.tagType)
//                )
//            )
//          })
//      )
//      .applyOptional(peopleIdsToUse)(
//        peopleCreditSearchQuery
//      )
//      .applyOptional(itemTypesToUse.filter(_.nonEmpty))(
//        itemTypesFilter
//      )
//      .applyOptional(genreIdsToUse.map(_.toSet).filter(_.nonEmpty))(
//        genreIdsFilter
//      )
//      .applyOptional(
//        networkIdsToUse.filter(_.nonEmpty)
//      )(
//        availabilityByNetworkIdsOr
//      )
//      .applyOptional(releaseYearRule.filter(_.isFinite))(openDateRangeFilter)
//      .applyIf(excludeWatchedItems)(excludeWatchedItemsFilter(userId, _))
//      .applyOptional(bookmark)(applyBookmark(_, _, Some(dynamicList)))
//
//    val defaultSort =
//      dynamicList.rules.flatMap(_.sort).map(_.sort).map(SortMode.fromString)
//    val sortToUse =
//      bookmark.map(_.sortMode).orElse(sortMode).orElse(defaultSort)
//
//    sourceBuilder
//      .query(baseBoolQuery)
//      .applyOptional(sortToUse)(
//        (builder, sort) => {
//          builder
//            .applyOptional(makeDefaultSort(sort))(_.sort(_))
//            .sort(
//              new FieldSortBuilder("id").order(SortOrder.ASC)
//            )
//        }
//      )
//      .applyOptional(limit)(_.size(_))
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

        case Rating(desc, source) =>
          item.ratingsGrouped
            .get(source)
            .flatMap(_.weighted_average)
            .getOrElse(if (desc) Double.MinValue else Double.MaxValue)
            .toString

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
