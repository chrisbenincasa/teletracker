package com.teletracker.common.elasticsearch

import com.teletracker.common.db.{
  Bookmark,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import com.teletracker.common.db.access.SearchOptions
import com.teletracker.common.db.model.{ExternalId, ExternalSource, ThingType}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Slug
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.{BoolQueryBuilder, Operator, QueryBuilders}
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders
import org.elasticsearch.indices.TermsLookup
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ItemSearch @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def searchItems(
    textQuery: String,
    searchOptions: SearchOptions
  ): Future[ElasticsearchItemsResponse] = {
    if (searchOptions.bookmark.isDefined) {
      require(searchOptions.bookmark.get.sortType == SortMode.SearchScoreType)
    }

    val searchQuery = QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .matchQuery("original_title", textQuery)
          .operator(Operator.AND)
      )
      .through(removeAdultItems)
      .applyOptional(searchOptions.thingTypeFilter.filter(_.nonEmpty))(
        (builder, types) => types.foldLeft(builder)(itemTypeFilter)
      )
//      .applyOptional(searchOptions.bookmark)(applyBookmark)

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

    val search = new SearchRequest()
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

  def lookupItemsByTitleExact(
    titles: List[(String, Option[ThingType], Option[Range])] // TODO: Accept a range
  ): Future[Map[String, EsItem]] = {
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

  def lookupItemByTitleExact(
    title: String,
    thingType: Option[ThingType],
    releaseYear: Option[Range]
  ): Future[Option[EsItem]] = {
    singleItemSearch(exactTitleMatchQuery(title, thingType, releaseYear))
  }

  def lookupItemsBySlug(
    slugs: List[(Slug, ThingType, Option[Range])]
  ): Future[Map[Slug, EsItem]] = {
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
        QueryBuilders.matchQuery("original_title", title)
      )
      .should(
        QueryBuilders.matchQuery("title", title)
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
