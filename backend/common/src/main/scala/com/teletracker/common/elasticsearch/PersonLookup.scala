package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.{Bookmark, Recent, SearchScore, SortMode}
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  PersonAssociationType
}
import com.teletracker.common.elasticsearch.cache.ExternalIdMappingCache
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{EsExternalId, EsPerson}
import com.teletracker.common.util.{Folds, IdOrSlug, Slug}
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.{
  MatchBoolPrefixQueryBuilder,
  Operator,
  QueryBuilders
}
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.UUID
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Maps._
import org.elasticsearch.action.get.MultiGetRequest
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class PersonLookup @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  itemSearch: ItemSearch,
  teletrackerConfig: TeletrackerConfig,
  externalIdMappingStore: ElasticsearchExternalIdMappingStore,
  externalIdMappingCache: ExternalIdMappingCache
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def fullTextSearch(
    textQuery: String,
    searchOptions: SearchOptions
  ): Future[ElasticsearchPeopleResponse] = {
    if (searchOptions.bookmark.isDefined) {
      require(searchOptions.bookmark.get.sortType == SortMode.SearchScoreType)
    }

    // TODO: Support all of the filters that regular search does

    val isMultiName = textQuery.split(" ").length > 1

    // Until we can do a better prefix search always with a reindex search_as_you_type field, this will suffice.
    val matchQuery = if (isMultiName) {
      new MatchBoolPrefixQueryBuilder("name", textQuery)
    } else {
      QueryBuilders.matchQuery("name", textQuery)
    }

    val searchQuery = QueryBuilders
      .boolQuery()
      .should(
        matchQuery
      )
      .minimumShouldMatch(1)
      .filter(QueryBuilders.rangeQuery("popularity").gte(1.0))
      .applyOptional(searchOptions.thingTypeFilter.filter(_.nonEmpty))(
        (builder, types) => types.foldLeft(builder)(itemTypeFilter)
      )

    val finalQuery = if (isMultiName) {
      searchQuery
    } else {
      // Boost initial results by popularity
      QueryBuilders.functionScoreQuery(
        searchQuery,
        ScoreFunctionBuilders
          .fieldValueFactorFunction("popularity")
          .factor(1.1f)
          .missing(0.8)
          .modifier(FieldValueFactorFunction.Modifier.SQRT)
      )
    }

    val searchSource = new SearchSourceBuilder()
      .query(finalQuery)
      .size(searchOptions.limit)
      .applyOptional(searchOptions.bookmark)((builder, bookmark) => {
        builder.from(bookmark.value.toInt)
      })

    println(searchSource)

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(
          searchSource
        )

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToPeople)
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

  def lookupPeopleByExternalIds(
    ids: Set[EsExternalId]
  ): Future[Map[EsExternalId, EsPerson]] = {
    val keys = ids.map(_ -> ItemType.Person)
    val cachedIds = externalIdMappingCache.getAll(keys)
    val missing = ids -- cachedIds.keySet.map(_._1)
    val missingKeys = missing.map(_ -> ItemType.Person)

    val storeLookupFut = if (missing.nonEmpty) {
      externalIdMappingStore
        .getItemIdsForExternalIds(missingKeys)
    } else {
      Future.successful(Map.empty[(EsExternalId, ItemType), UUID])
    }

    storeLookupFut.flatMap(foundMappings => {
      val keysNeedingSearch = missingKeys -- foundMappings.keySet
      val peopleFromSearch = if (keysNeedingSearch.nonEmpty) {
        lookupExternalIdsViaSearch(keysNeedingSearch.map(_._1).toList)
      } else {
        Future.successful(Map.empty[EsExternalId, EsPerson])
      }

      val allMappings = (cachedIds ++ foundMappings).map {
        case ((k, _), uuid) => k -> uuid
      }

      val externalIdsById = allMappings.reverse
      val allIds = allMappings.values.toSet

      val directLookupResultFut = lookupPeople(
        allIds.map(IdOrSlug.fromUUID).toList
      ).map(people => {
        people
          .flatMap(
            person => externalIdsById.get(person.id).map(_.map(_ -> person))
          )
          .foldLeft(Map.empty[EsExternalId, EsPerson])(_ ++ _)
      })

      for {
        directLookupResult <- directLookupResultFut
        searchResult <- peopleFromSearch
      } yield {
        directLookupResult ++ searchResult
      }
    })
  }

  private def lookupExternalIdsViaSearch(
    externalIds: List[EsExternalId]
  ): Future[Map[EsExternalId, EsPerson]] = {
    if (externalIds.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searchSources = externalIds.map(id => {
        val query = QueryBuilders
          .boolQuery()
          .filter(
            QueryBuilders
              .termQuery("external_ids", id.toString)
          )

        new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
          .source(new SearchSourceBuilder().query(query).size(1))
      })

      val request = new MultiSearchRequest()

      searchSources.foreach(request.add)

      elasticsearchExecutor
        .multiSearch(request)
        .map(response => {
          response.getResponses
            .zip(externalIds)
            .flatMap {
              case (responseItem, _) if responseItem.isFailure => // Log
                None

              case (responseItem, id) =>
                searchResponseToPeople(responseItem.getResponse).items.headOption
                  .map(id -> _)

            }
            .toMap
        })
    }
  }

  def lookupPeopleByExternalIds(
    source: ExternalSource,
    ids: List[String]
  ): Future[Map[String, EsPerson]] = {
    val searchSources = ids.map(id => {
      val query = QueryBuilders
        .boolQuery()
        .filter(
          QueryBuilders
            .termQuery("external_ids", EsExternalId(source, id).toString)
        )

      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(new SearchSourceBuilder().query(query).size(1))
    })

    val request = new MultiSearchRequest()

    searchSources.foreach(request.add)

    elasticsearchExecutor
      .multiSearch(request)
      .map(response => {
        response.getResponses
          .zip(ids)
          .flatMap {
            case (responseItem, _) if responseItem.isFailure => // Log
              None

            case (responseItem, id) =>
              searchResponseToPeople(responseItem.getResponse).items.headOption
                .map(id -> _)

          }
          .toMap
      })
  }

  def lookupPersonByExternalId(
    source: ExternalSource,
    id: String
  ): Future[Option[EsPerson]] = {
    val query = QueryBuilders
      .boolQuery()
      .filter(
        QueryBuilders
          .termQuery("external_ids", EsExternalId(source, id).toString)
      )

    val searchSource = new SearchSourceBuilder().query(query).size(1)

    elasticsearchExecutor
      .search(
        new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
          .source(searchSource)
      )
      .map(searchResponseToPeople)
      .map(_.items.headOption)
  }

  def lookupPerson(
    identifier: Either[UUID, Slug],
    materializeCredits: Boolean,
    creditsLimit: Option[Int]
  ): Future[Option[(EsPerson, ElasticsearchItemsResponse)]] = {
    val identifierQuery = identifier match {
      case Left(value) =>
        QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("id", value.toString))

      case Right(value) =>
        QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("slug", value.toString))
    }

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(new SearchSourceBuilder().query(identifierQuery).size(1))

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToPeople)
      .map(response => {
        response.items.headOption
      })
      .flatMap {
        case None => Future.successful(None)
        case Some(person)
            if !materializeCredits || creditsLimit.exists(_ <= 0) =>
          Future.successful(Some(person -> ElasticsearchItemsResponse.empty))
        case Some(person) =>
          lookupPersonCastCredits(
            person.id,
            creditsLimit.getOrElse(person.cast_credits.getOrElse(Nil).size)
          ).map(credits => Some(person -> credits))
      }
  }

  def lookupPeople(identifiers: List[IdOrSlug]): Future[List[EsPerson]] = {
    val (withId, withSlug) = identifiers.partition(_.id.isDefined)

    val ids = withId.flatMap(_.id).map(_.toString)

    val idPeopleFut = if (ids.nonEmpty) {
      val multiGetRequest = new MultiGetRequest()
      ids.foreach(
        multiGetRequest
          .add(teletrackerConfig.elasticsearch.people_index_name, _)
      )
      elasticsearchExecutor
        .multiGet(multiGetRequest)
        .map(idResults => {
          idResults.getResponses.toList
            .filter(!_.isFailed)
            .map(_.getResponse)
            .flatMap(
              getResponse =>
                decodeSourceString[EsPerson](getResponse.getSourceAsString)
            )
        })
    } else {
      Future.successful(Nil)
    }

    val slugs = withSlug.flatMap(_.slug).map(_.toString)

    val slugPeopleFut = if (slugs.nonEmpty) {
      val multiSearchRequest = new MultiSearchRequest()
      val slugSearches = slugs.map(slug => {
        val query = QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("slug", slug.toString))

        new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
          .source(new SearchSourceBuilder().query(query).size(1))
      })
      slugSearches.foreach(multiSearchRequest.add)

      elasticsearchExecutor
        .multiSearch(multiSearchRequest)
        .map(slugResults => {
          slugResults.getResponses
            .filter(!_.isFailure)
            .toList
            .flatMap(
              response =>
                searchResponseToPeople(response.getResponse).items.headOption
            )
        })
    } else {
      Future.successful(Nil)
    }

    for {
      extractedIdPeople <- idPeopleFut
      extractedSlugPeople <- slugPeopleFut
    } yield {
      extractedIdPeople ++ extractedSlugPeople
    }
  }

  def lookupPersonBySlug(
    slug: Slug,
    throwOnMultipleSlugs: Boolean = false
  ): Future[Option[EsPerson]] = {
    lookupPeopleBySlugs(List(slug), throwOnMultipleSlugs)
      .map(_.get(slug).headOption)
  }

  def lookupPeopleBySlugPrefix(
    slug: Slug
  ): Future[ElasticsearchPeopleResponse] = {
    val slugQuery = QueryBuilders
      .boolQuery()
      .filter(QueryBuilders.prefixQuery("slug", slug.toString))

    val request =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(new SearchSourceBuilder().query(slugQuery))

    elasticsearchExecutor.search(request).map(searchResponseToPeople)
  }

  def lookupPeopleBySlugs(
    slugs: List[Slug],
    throwOnMultipleSlugs: Boolean = false
  ): Future[Map[Slug, EsPerson]] = {
    lookupDupePeopleBySlugs(slugs, throwOnMultipleSlugs).map(
      _.mapValues(_.head)
    )
  }

  def lookupDupePeopleBySlugs(
    slugs: List[Slug],
    throwOnMultipleSlugs: Boolean = false
  ): Future[Map[Slug, List[EsPerson]]] = {
    if (slugs.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val multiSearchRequest = new MultiSearchRequest()
      val searches = slugs.map(slug => {
        val slugQuery = QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("slug", slug.toString))

        new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
          .source(
            new SearchSourceBuilder()
              .query(slugQuery)
              .applyIf(!throwOnMultipleSlugs)(_.size(1))
          )
      })

      searches.foreach(multiSearchRequest.add)

      elasticsearchExecutor
        .multiSearch(multiSearchRequest)
        .map(response => {
          response.getResponses.map(response => {
            // TODO: Properly handle failures
            searchResponseToPeople(response.getResponse).items
              .filter(_.slug.isDefined)
              .groupBy(_.slug.get)

          })
        })
        .map(
          _.foldLeft(Map.empty[Slug, List[EsPerson]])(
            Folds.foldMapAppend[Slug, EsPerson, List]
          )
        )
        .andThen {
          case Success(value) =>
            value.applyIf(throwOnMultipleSlugs)(map => {
              map.foreach {
                case (slug, people) =>
                  if (people.size > 1) {
                    throw new IllegalStateException(
                      s"Found multiple people associated with slug: $slug"
                    )
                  }
              }

              map
            })
        }
    }
  }

  private def lookupPersonCastCredits(
    personId: UUID,
    limit: Int
  ) = {
    itemSearch.searchItems(
      genres = None,
      networks = None,
      itemTypes = None,
      sortMode = Recent(),
      limit = limit,
      bookmark = None,
      releaseYear = None,
      peopleCreditSearch = Some(
        PeopleCreditSearch(
          Seq(
            PersonCreditSearch(
              IdOrSlug.fromUUID(personId),
              PersonAssociationType.Cast
            )
          ),
          BinaryOperator.Or
        )
      ),
      imdbRatingRange = None
    )
  }
}
