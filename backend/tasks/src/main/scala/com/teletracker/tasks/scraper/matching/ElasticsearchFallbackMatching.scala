package com.teletracker.tasks.scraper.matching

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsItem
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.model.{NonMatchResult, PotentialMatch}
import com.teletracker.tasks.scraper.{
  model,
  IngestJob,
  IngestJobArgs,
  IngestJobArgsLike,
  ScrapedItem
}
import io.circe.Codec
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{
  MultiMatchQueryBuilder,
  Operator,
  QueryBuilder,
  QueryBuilders
}
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

case class ElasticsearchFallbackMatcherOptions(
  requireTypeMatch: Boolean,
  sourceJobName: String,
  returnResults: Boolean = false)

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
        performMultiSearchWithQuery(group, combinedQuery2[T])
          .andThen {
            case Success(potentialMatches) =>
              recordPotentialMatches(potentialMatches)
          }
          .map(potentialMatches => {
            potentialMatches.map {
              case (esItem, scrapedItem) =>
                model.NonMatchResult(
                  scrapedItem,
                  scrapedItem,
                  esItem
                )
            }
          })
//        performFuzzyTitleMatchSearch(group)
//          .andThen {
//            case Success(potentialMatches) =>
//              recordPotentialMatches(potentialMatches)
//          }
//          .flatMap(potentialMatches => {
//            val missing = group.toSet -- potentialMatches.map(_._2)
//            val hasDescription = missing.filter(_.description.isDefined)
//
//            if (hasDescription.isEmpty) {
//              Future.successful {
//                potentialMatches.map {
//                  case (esItem, scrapedItem) =>
//                    model.NonMatchResult(
//                      scrapedItem,
//                      scrapedItem,
//                      esItem
//                    )
//                }
//              }
//            } else {
//              performDescriptionMatchSearch(hasDescription).map(matches => {
//                recordPotentialMatches(matches)
//
//                (potentialMatches ++ matches).map {
//                  case (esItem, scrapedItem) =>
//                    model.NonMatchResult(
//                      scrapedItem,
//                      scrapedItem,
//                      esItem
//                    )
//                }
//              })
//            }
//          })
      })
      .map(_.flatten)
      .map(results => {
        if (options.returnResults) {
          results
        } else {
          Nil
        }
      })
  }

  private def performFuzzyTitleMatchSearch[T <: ScrapedItem: Codec](
    group: Iterable[T]
  ) = {
    performMultiSearchWithQuery(group, fuzzyTitleMatchQuery[T])
  }

  private def performDescriptionMatchSearch[T <: ScrapedItem: Codec](
    group: Iterable[T]
  ) = {
    performMultiSearchWithQuery(group, fuzzyDescriptionQuery[T])
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
  ): Unit = {
//    potentialMatches
//      .map(Function.tupled(PotentialMatch.forEsItem))
//      .foreach(potentialMatch => {
//        os.println(
//          potentialMatch.asJson.noSpaces
//        )
//      })
//
//    os.flush()
  }

  private def combinedQuery[T <: ScrapedItem: Codec](nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .boolQuery()
          .should(
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
          .applyOptional(nonMatch.description)((builder, desc) => {
            builder.should(
              QueryBuilders
                .matchPhraseQuery("overview", desc)
//                .operator(Operator.OR)
            )
          })
          .minimumShouldMatch(1)
      )
      .must(
        QueryBuilders.existsQuery("release_date")
      )
      .applyIf(options.requireTypeMatch && nonMatch.thingType.isDefined)(
        _.filter(
          QueryBuilders
            .termQuery("type", nonMatch.thingType.get.getName.toLowerCase())
        )
      )
      .applyOptional(nonMatch.releaseYear)(
        (builder, ry) =>
          builder
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"$ry||/y")
                .lte(s"$ry||/y")
                .boost(3)
            )
            .should(
              QueryBuilders
                .rangeQuery("release_date")
                .format("yyyy")
                .gte(s"${ry - 1}||/y")
                .lte(s"${ry + 1}||/y")
            )
      )
  }

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
      .applyIf(options.requireTypeMatch && nonMatch.thingType.isDefined)(
        _.filter(
          QueryBuilders
            .termQuery("type", nonMatch.thingType.get.getName.toLowerCase())
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

  private def fuzzyTitleMatchQuery[T <: ScrapedItem: Codec](nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .should(
        QueryBuilders
          .multiMatchQuery(nonMatch.title)
          .field("title", 1.2f)
          .field("original_title")
          .operator(Operator.OR)
      )
      .minimumShouldMatch(1)
      .applyIf(options.requireTypeMatch && nonMatch.thingType.isDefined)(
        _.filter(
          QueryBuilders
            .termQuery("type", nonMatch.thingType.get.getName.toLowerCase())
        )
      )
      .applyOptional(nonMatch.releaseYear)(
        (builder, ry) =>
          builder.filter(
            QueryBuilders
              .rangeQuery("release_date")
              .format("yyyy")
              .gte(s"${ry - 1}||/y")
              .lte(s"${ry + 1}||/y")
          )
      )
  }

  private def fuzzyDescriptionQuery[T <: ScrapedItem: Codec](nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .matchQuery("overview", nonMatch.description.get)
          .operator(Operator.OR)
      )
      .applyIf(options.requireTypeMatch && nonMatch.thingType.isDefined)(
        _.filter(
          QueryBuilders
            .termQuery("type", nonMatch.thingType.get.getName.toLowerCase())
        )
      )
      .applyOptional(nonMatch.releaseYear)(
        (builder, ry) =>
          builder.should(
            QueryBuilders
              .rangeQuery("release_date")
              .format("yyyy")
              .gte(s"${ry - 1}||/y")
              .lte(s"${ry + 1}||/y")
          )
      )
  }
}

trait ElasticsearchFallbackMatching[T <: ScrapedItem]
    extends ElasticsearchAccess {
  self: IngestJob[T] =>

  protected def requireTypeMatch: Boolean = true

  protected def teletrackerConfig: TeletrackerConfig
  protected def elasticsearchExecutor: ElasticsearchExecutor

  private lazy val matcher = new ElasticsearchFallbackMatcher(
    teletrackerConfig,
    elasticsearchExecutor,
    ElasticsearchFallbackMatcherOptions(
      requireTypeMatch,
      getClass.getSimpleName
    )
  ) {
    override protected def recordPotentialMatches[X <: ScrapedItem: Codec](
      potentialMatches: Iterable[(EsItem, X)]
    ): Unit = {
      self.writePotentialMatches(potentialMatches.map {
        case (esItem, x) =>
          esItem -> x.asInstanceOf[T] // We know this is correct
      })
    }
  }

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    matcher.handleNonMatches(
      args,
      nonMatches
    )
  }
}
