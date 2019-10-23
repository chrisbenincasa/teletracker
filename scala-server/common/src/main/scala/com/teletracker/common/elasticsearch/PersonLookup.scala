package com.teletracker.common.elasticsearch

import com.teletracker.common.db.Recent
import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.util.Slug
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.QueryBuilders
import com.teletracker.common.util.Functions._
import org.elasticsearch.indices.TermsLookup
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PersonLookup @Inject()(elasticsearchExecutor: ElasticsearchExecutor)
    extends ElasticsearchAccess {

  def lookupPeopleByExternalIds(
    source: ExternalSource,
    ids: List[String]
  )(implicit executionContext: ExecutionContext
  ): Future[Map[String, EsPerson]] = {
    val searchSources = ids.map(id => {
      val query = QueryBuilders
        .boolQuery()
        .filter(
          QueryBuilders
            .termQuery("external_ids", EsExternalId(source, id).toString)
        )

      new SearchRequest().source(new SearchSourceBuilder().query(query).size(1))
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
  )(implicit executionContext: ExecutionContext
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
  )(implicit executionContext: ExecutionContext
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

    val search = new SearchRequest()
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
          lookupPersonCredits(
            person.id,
            person.cast_credits.getOrElse(Nil).size
          ).map(credits => Some(person -> credits))
      }
  }

  private def lookupPersonCredits(
    id: UUID,
    limit: Int
  )(implicit executionContext: ExecutionContext
  ) = {
    val query = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termsLookupQuery(
            "id",
            new TermsLookup("people", id.toString, "cast_credits.id")
          )
      )

    val search = new SearchRequest("items")
      .source(
        new SearchSourceBuilder()
          .query(query)
          .applyOptional(makeSort(Recent()))(_.sort(_))
          .size(limit)
      )

    elasticsearchExecutor.search(search).map(searchResponseToItems)
  }
}
