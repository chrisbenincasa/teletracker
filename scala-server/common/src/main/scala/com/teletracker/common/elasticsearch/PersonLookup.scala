package com.teletracker.common.elasticsearch

import com.teletracker.common.db.{
  Bookmark,
  Recent,
  SearchOptions,
  SearchScore,
  SortMode
}
import com.teletracker.common.db.model.{ExternalSource, PersonAssociationType}
import com.teletracker.common.util.{IdOrSlug, Slug}
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
import org.elasticsearch.action.get.MultiGetRequest
import scala.concurrent.{ExecutionContext, Future}

class PersonLookup @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  itemSearch: ItemSearch
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

    val search = new SearchRequest("people")
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

      new SearchRequest("people")
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
      .search(new SearchRequest("people").source(searchSource))
      .map(searchResponseToPeople)
      .map(_.items.headOption)
  }

  def lookupPerson(
    identifier: Either[UUID, Slug]
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

    val search = new SearchRequest("people")
      .source(new SearchSourceBuilder().query(identifierQuery).size(1))

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToPeople)
      .map(response => {
        response.items.headOption
      })
      .flatMap {
        case None => Future.successful(None)
        case Some(person) =>
          lookupPersonCastCredits(
            person.id,
            person.cast_credits.getOrElse(Nil).size
          ).map(credits => Some(person -> credits))
      }
  }

  def lookupPeople(identifiers: List[IdOrSlug]): Future[List[EsPerson]] = {
    val (withId, withSlug) = identifiers.partition(_.id.isDefined)

    val ids = withId.flatMap(_.id).map(_.toString)

    val idPeopleFut = if (ids.nonEmpty) {
      val multiGetRequest = new MultiGetRequest()
      ids.foreach(multiGetRequest.add("people", _))
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

        new SearchRequest("people")
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

  def lookupPersonBySlug(slug: Slug): Future[Option[EsPerson]] = {
    lookupPeopleBySlugs(List(slug)).map(_.get(slug))
  }

  def lookupPeopleBySlugs(slugs: List[Slug]): Future[Map[Slug, EsPerson]] = {
    if (slugs.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val multiSearchRequest = new MultiSearchRequest()
      val searches = slugs.map(slug => {
        val slugQuery = QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("slug", slug.toString))

        new SearchRequest("people")
          .source(new SearchSourceBuilder().query(slugQuery).size(1))
      })

      searches.foreach(multiSearchRequest.add)

      elasticsearchExecutor
        .multiSearch(multiSearchRequest)
        .map(response => {
          response.getResponses
            .zip(slugs)
            .flatMap {
              case (response, slug) =>
                // TODO: Properly handle failures
                searchResponseToPeople(response.getResponse).items.headOption
                  .map(slug -> _)
            }
            .toMap
        })
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
      )
    )
  }
}
