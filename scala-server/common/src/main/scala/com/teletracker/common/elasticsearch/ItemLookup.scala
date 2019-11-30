package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Slug
import javax.inject.Inject
import org.elasticsearch.action.get.{GetRequest, MultiGetRequest}
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{BoolQueryBuilder, Operator, QueryBuilders}
import org.elasticsearch.indices.TermsLookup
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ItemLookup @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def getItemsById(ids: Set[UUID]): Future[Map[UUID, Option[EsItem]]] = {
    if (ids.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val multiGetRequest = new MultiGetRequest()
      ids.toList
        .map(id => new MultiGetRequest.Item("items", id.toString))
        .foreach(multiGetRequest.add)

      elasticsearchExecutor
        .multiGet(multiGetRequest)
        .map(response => {
          response.getResponses.toList
            .flatMap(response => {
              val id = response.getId
              if (!response.isFailed) {
                None
              } else {
                Some(
                  UUID.fromString(id) -> decodeSourceString[EsItem](
                    response.getResponse.getSourceAsString
                  )
                )
              }
            })
            .toMap
        })
    }
  }

  def lookupItemByExternalId(
    source: ExternalSource,
    id: String,
    thingType: ThingType
  ): Future[Option[EsItem]] = {
    val query = QueryBuilders
      .boolQuery()
      .filter(
        QueryBuilders
          .termQuery("external_ids", EsExternalId(source, id).toString)
      )
      .filter(QueryBuilders.termQuery("type", thingType.toString))

    singleItemSearch(query)
  }

  def lookupItemsByExternalIds(
    items: List[(ExternalSource, String, ThingType)]
  ): Future[Map[(ExternalSource, String), EsItem]] = {
    if (items.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searches = items.map {
        case (source, id, typ) =>
          val query = QueryBuilders
            .boolQuery()
            .filter(
              QueryBuilders
                .termQuery("external_ids", EsExternalId(source, id).toString)
            )
            .filter(QueryBuilders.termQuery("type", typ.toString))

          new SearchRequest("items")
            .source(new SearchSourceBuilder().query(query).size(1))
      }

      val multiReq = new MultiSearchRequest()
      searches.foreach(multiReq.add)

      elasticsearchExecutor
        .multiSearch(multiReq)
        .map(resp => {
          resp.getResponses.toList
            .zip(items.map(item => item._1 -> item._2))
            .map {
              case (response, sourceAndId) =>
                searchResponseToItems(response.getResponse).items.headOption
                  .map(sourceAndId -> _)
            }
        })
        .map(_.flatten.toMap)
    }
  }

  def lookupItemsByTitleMatch(
    titles: List[(String, Option[ThingType], Option[Range])] // TODO: Accept a range
  ): Future[Map[String, EsItem]] = {
    if (titles.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searches = titles
        .map(Function.tupled(exactTitleMatchQuery))
        .map(query => {
          val searchSource = new SearchSourceBuilder().query(query).size(1)
          new SearchRequest("items").source(searchSource)
        })

      val multiReq = new MultiSearchRequest()
      searches.foreach(multiReq.add)

      elasticsearchExecutor
        .multiSearch(multiReq)
        .map(resp => {
          resp.getResponses.toList.zip(titles.map(_._1)).map {
            case (response, title) =>
              searchResponseToItems(response.getResponse).items.headOption
                .map(title -> _)
          }
        })
        .map(_.flatten.toMap)
    }

  }

  def lookupItemByTitleMatch(
    title: String,
    thingType: Option[ThingType],
    releaseYear: Option[Range]
  ): Future[Option[EsItem]] = {
    singleItemSearch(exactTitleMatchQuery(title, thingType, releaseYear))
  }

  def lookupItemsBySlug(
    slugs: List[(Slug, ThingType, Option[Range])]
  ): Future[Map[Slug, EsItem]] = {
    if (slugs.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val searches = slugs
        .map(Function.tupled(slugMatchQuery))
        .map(query => {
          val searchSource = new SearchSourceBuilder().query(query).size(1)
          new SearchRequest("items").source(searchSource)
        })

      val multiReq = new MultiSearchRequest()
      searches.foreach(multiReq.add)

      elasticsearchExecutor
        .multiSearch(multiReq)
        .map(resp => {
          resp.getResponses.toList.zip(slugs.map(_._1)).map {
            case (response, title) =>
              searchResponseToItems(response.getResponse).items.headOption
                .map(title -> _)
          }
        })
        .map(_.flatten.toMap)
    }
  }

  def lookupItemBySlug(
    slug: Slug,
    thingType: ThingType,
    releaseYear: Option[Range]
  ): Future[Option[EsItem]] = {
    singleItemSearch(slugMatchQuery(slug, thingType, releaseYear))
  }

  def lookupItem(
    identifier: Either[UUID, Slug],
    thingType: Option[ThingType],
    materializeJoins: Boolean = true
  ): Future[Option[ItemLookupResponse]] = {
    val identifierQuery = identifier match {
      case Left(value) =>
        QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("id", value.toString))

      case Right(value) =>
        require(thingType.isDefined)

        QueryBuilders
          .boolQuery()
          .filter(QueryBuilders.termQuery("slug", value.toString))
    }

    val query = identifierQuery.applyOptional(thingType)(
      (builder, typ) =>
        builder.filter(
          QueryBuilders.termQuery("type", typ.toString)
        )
    )

    val search = new SearchRequest()
      .source(new SearchSourceBuilder().query(query).size(1))

    elasticsearchExecutor
      .search(search)
      .map(searchResponseToItems)
      .map(response => {
        response.items.headOption
      })
      .flatMap {
        case None =>
          Future.successful(None)
        case Some(item) =>
          val recommendationsOrder =
            item.recommendations.getOrElse(Nil).map(_.id).zipWithIndex.toMap

          val recsFut =
            if (materializeJoins) materializeRecommendations(item.id)
            else Future.successful(ElasticsearchItemsResponse.empty)

          val castFut =
            if (materializeJoins)
              lookupItemCredits(item.id, item.cast.getOrElse(Nil).size)
            else Future.successful(ElasticsearchPeopleResponse.empty)

          for {
            recs <- recsFut
            cast <- castFut
          } yield {
            Some(
              ItemLookupResponse(
                item,
                cast,
                recs.copy(
                  items = recs.items.sortBy(
                    i => recommendationsOrder.getOrElse(i.id, Int.MaxValue)
                  )
                )
              )
            )
          }
      }
  }

  private def slugMatchQuery(
    slug: Slug,
    thingType: ThingType,
    releaseYear: Option[Range]
  ) = {
    QueryBuilders
      .boolQuery()
      .filter(
        QueryBuilders
          .termQuery("slug", slug.value)
      )
      .filter(QueryBuilders.termQuery("type", thingType.toString))
      .applyOptional(releaseYear)(releaseYearRangeQuery)
  }

  private def exactTitleMatchQuery(
    title: String,
    thingType: Option[ThingType],
    releaseYear: Option[Range]
  ) = {
    QueryBuilders
      .boolQuery()
      .should(
        QueryBuilders.matchQuery("original_title", title).operator(Operator.AND)
      )
      .should(
        QueryBuilders.matchQuery("title", title).operator(Operator.AND)
      )
      .minimumShouldMatch(1)
      .applyOptional(thingType)(
        (builder, typ) =>
          builder.filter(
            QueryBuilders
              .termQuery("type", typ.toString)
          )
      )
      .applyOptional(releaseYear)(releaseYearRangeQuery)
  }

  private def releaseYearRangeQuery(
    builder: BoolQueryBuilder,
    range: Range
  ) = {
    builder.filter(
      QueryBuilders
        .rangeQuery("release_date")
        .format("yyyy")
        .gte(s"${range.head}||/y")
        .lte(s"${range.last}||/y")
    )
  }

//  private def executeMultisearch()

  private def singleItemSearch(query: BoolQueryBuilder) = {
    val searchSource = new SearchSourceBuilder().query(query).size(1)

    elasticsearchExecutor
      .search(new SearchRequest("items").source(searchSource))
      .map(searchResponseToItems)
      .map(_.items.headOption)
  }

  private def materializeRecommendations(
    id: UUID
  ): Future[ElasticsearchItemsResponse] = {
    val query = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termsLookupQuery(
            "id",
            new TermsLookup("items", id.toString, "recommendations.id")
          )
      )

    val search = new SearchRequest("items")
      .source(
        new SearchSourceBuilder()
          .query(query)
          .size(
            20
          )
      )

    elasticsearchExecutor.search(search).map(searchResponseToItems)
  }

  private def lookupItemCredits(
    id: UUID,
    limit: Int
  )(implicit executionContext: ExecutionContext
  ): Future[ElasticsearchPeopleResponse] = {
    val query = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termsLookupQuery(
            "id",
            new TermsLookup("items", id.toString, "cast.id")
          )
      )

    val search = new SearchRequest("people")
      .source(
        new SearchSourceBuilder()
          .query(query)
          .size(limit)
      )

    elasticsearchExecutor.search(search).map(searchResponseToPeople)
  }
}

case class ItemLookupResponse(
  rawItem: EsItem,
  materializedCast: ElasticsearchPeopleResponse,
  materializedRecommendations: ElasticsearchItemsResponse)
