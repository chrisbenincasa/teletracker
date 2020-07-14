package com.teletracker.tasks.scraper.matching

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  FuzzyItemLookupRequest,
  ItemLookup
}
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapedItem}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.RelativeRange
import com.teletracker.tasks.scraper.IngestJobArgsLike
import io.circe.Codec
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{
  MultiMatchQueryBuilder,
  Operator,
  QueryBuilder,
  QueryBuilders
}
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchFallbackMatcherOptions(
  requireTypeMatch: Boolean,
  sourceJobName: String)

object ElasticsearchFallbackMatcher {
  trait Factory {
    def create(
      options: ElasticsearchFallbackMatcherOptions
    ): ElasticsearchFallbackMatcher
  }
}

class ElasticsearchFallbackMatcher @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor,
  itemLookup: ItemLookup,
  @Assisted options: ElasticsearchFallbackMatcherOptions
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  protected lazy val today = LocalDate.now()

  def handleNonMatches[T <: ScrapedItem: Codec](
    args: IngestJobArgsLike,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    nonMatches
      .grouped(10)
      .toList
      .sequentially(group => {
        val requests = group.map(item => {
          val fuzzyReq = FuzzyItemLookupRequest(
            title = item.title,
            strictTitleMatch = false,
            description = item.description,
            itemType = Some(item.itemType),
            exactReleaseYear = item.releaseYear.map(_ -> 5.0f),
            releaseYearTiers = Some(
              Seq(
                RelativeRange.forInt(1) -> 2.0f,
                RelativeRange(5, -1) -> 1.0f,
                RelativeRange(-1, 5) -> 1.0f
              )
            ),
            looseReleaseYearMatching = false,
            popularityThreshold = Some(1.0),
            castNames = item.cast.filter(_.nonEmpty).map(_.map(_.name).toSet),
            crewNames = item.crew.filter(_.nonEmpty).map(_.map(_.name).toSet),
            limit = 1
          )

          item -> fuzzyReq
        })

        val itemToFuzzyRequest = requests.map {
          case (t, request) => request.id -> t
        }.toMap

        itemLookup
          .lookupFuzzyBatch(requests.map(_._2))
          .map(results => {
            val potentialMatches = itemToFuzzyRequest.toList.flatMap {
              case (requestId, item) =>
                results
                  .get(requestId)
                  .flatMap(_.items.headOption)
                  .map(_ -> item)
            }

            recordPotentialMatches(potentialMatches)

            potentialMatches.map {
              case (item, t) => NonMatchResult(t, t, item)
            }
          })
      })
      .map(_.flatten)
  }

  private def performMultiSearchWithQuery[T <: ScrapedItem: Codec](
    group: Iterable[T],
    queryFn: T => QueryBuilder
  ) = {
    val exactMatchMultiSearch = new MultiSearchRequest()
    group
      .map(nonMatch => {
        val query = queryFn(nonMatch)
        new SearchRequest(teletrackerConfig.elasticsearch.items_index_name)
          .source(new SearchSourceBuilder().query(query).size(1))
      })
      .foreach(exactMatchMultiSearch.add)

    elasticsearchExecutor
      .multiSearch(
        exactMatchMultiSearch
      )
      .map(multiResponse => {
        multiResponse.getResponses.toList.zip(group).map {
          case (response, item) =>
            searchResponseToItems(response.getResponse).items.headOption
              .map(_ -> item)
        }
      })
      .map(_.flatten)
  }

  protected def recordPotentialMatches[T <: ScrapedItem: Codec](
    potentialMatches: Iterable[(EsItem, T)]
  ): Unit = {}

  private def combinedQuery2[T <: ScrapedItem: Codec](nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .multiMatchQuery(nonMatch.title)
          .field("title", 1.2f)
          .field("alternative_titles.title", 1.2f)
          .field("original_title")
          .boost(2)
          .operator(Operator.OR)
          .`type`(MultiMatchQueryBuilder.Type.BEST_FIELDS)
          .minimumShouldMatch("1")
      )
      .must(
        QueryBuilders.existsQuery("release_date")
      )
      .applyIf(options.requireTypeMatch)(
        _.filter(
          QueryBuilders
            .termQuery("type", nonMatch.itemType.getName.toLowerCase())
        )
      )
      .applyOptional(nonMatch.description)((builder, desc) => {
        builder.should(
          QueryBuilders
            .matchPhraseQuery("overview", desc)
        )
      })
      .applyOptional(nonMatch.releaseYear)(
        (builder, ry) =>
          builder
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"$ry||/y")
                .lte(s"$ry||/y")
                .boost(5)
            )
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"${ry - 1}||/y")
                .lte(s"${ry + 1}||/y")
                .boost(2)
            )
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"${ry - 5}||/y")
                .lte(s"${ry - 1}||/y")
            )
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"${ry + 1}||/y")
                .lte(s"${ry + 5}||/y")
            )
      )
  }
}
