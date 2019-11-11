package com.teletracker.common.elasticsearch

import com.teletracker.common.db.Recent
import com.teletracker.common.db.model.{
  ExternalSource,
  Network,
  PersonAssociationType
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.{IdOrSlug, Slug}
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.indices.TermsLookup
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PersonLookup @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  itemSearch: ItemSearch
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

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

  def lookupPersonBySlug(slug: Slug): Future[Option[EsPerson]] = {
    lookupPeopleBySlugs(List(slug)).map(_.get(slug))
  }

  def lookupPeopleBySlugs(slugs: List[Slug]): Future[Map[Slug, EsPerson]] = {
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
