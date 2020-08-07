package com.teletracker.common.elasticsearch

import com.teletracker.common.db._
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.model.{PersonAssociationType, UserThingTagType}
import com.teletracker.common.elasticsearch.model.EsItemTag.TagFormatter
import com.teletracker.common.elasticsearch.model.{
  EsPerson,
  ItemSearchParams,
  TagFilter
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util._
import javax.inject.Inject
import java.util.UUID
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
    list: StoredUserList,
    ruleOverrides: Option[ItemSearchParams]
  ): Future[Long] = {
    getDynamicListQuery(userId, list, ruleOverrides, None, None, None).flatMap(
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
    val countFut = getDynamicListItemCount(userId, list, ruleOverrides)

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

  def getRelevantPeople(list: StoredUserList): Future[List[EsPerson]] = {
    val peopleToFetch = list.rules.toList.flatMap(_.personRules).map(_.personId)
    if (peopleToFetch.nonEmpty) {
      personLookup.lookupPeople(
        peopleToFetch.map(IdOrSlug.fromUUID),
        includeBio = false
      )
    } else {
      Future.successful(Nil)
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
        // TODO: Hookup "any availability" rule to smart lists
        allNetworks = None,
        itemTypes = itemTypeFilters,
        releaseYear = releaseYearFilter,
        peopleCredits = personSearch,
        imdbRating = ruleOverrides.flatMap(_.imdbRating), // TODO: Support
        tagFilters = if (tagFilters.nonEmpty) Some(tagFilters) else None,
        titleSearch = None,
        sortMode = sortToUse.getOrElse(Popularity()),
        limit = limit.getOrElse(20),
        bookmark = bookmark,
        forList = Some(dynamicList),
        // TODO: Hookup availability rules to smart lists
        availability = None
      )
    }
  }
}
