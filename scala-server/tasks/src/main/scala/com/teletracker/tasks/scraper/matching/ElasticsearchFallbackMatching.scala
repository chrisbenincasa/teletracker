package com.teletracker.tasks.scraper.matching

import com.google.inject.assistedinject.Assisted
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
  ScrapedItem
}
import io.circe.Codec
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.index.query.{Operator, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
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
  elasticsearchExecutor: ElasticsearchExecutor,
  @Assisted options: ElasticsearchFallbackMatcherOptions
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  protected lazy val today = LocalDate.now()

  private val _outputFile = new File(
    s"${today}-${options.sourceJobName}_potential-matches.txt"
  )

  private val os = new PrintStream(
    new BufferedOutputStream(
      new FileOutputStream(
        _outputFile
      )
    )
  )

  def handleNonMatches[T <: ScrapedItem: Codec](
    args: IngestJobArgs,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    nonMatches
      .grouped(10)
      .toList
      .sequentially(group => {

        performFuzzyTitleMatchSearch(group).map(potentialMatches => {
          recordPotentialMatches(potentialMatches)

          potentialMatches.map {
            case (esItem, scrapedItem) =>
              model.NonMatchResult(
                scrapedItem,
                scrapedItem,
                esItem
              )
          }
        })
      })
      .map(_.flatten)
      .map(_ => {
        os.flush()
        // TODO: Actually return these
        Nil
      })
  }

  def flush(): Unit = os.flush()
  def outputFile: File = _outputFile

  private def performFuzzyTitleMatchSearch[T <: ScrapedItem: Codec](
    group: Iterable[T]
  ) = {
    val exactMatchMultiSearch = new MultiSearchRequest()
    group
      .map(nonMatch => {
        val query = exactTitleMatchQuery(nonMatch)
        new SearchRequest("items")
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

  private def recordPotentialMatches[T <: ScrapedItem: Codec](
    potentialMatches: Iterable[(EsItem, T)]
  ): Unit = {
    potentialMatches
      .map(Function.tupled(PotentialMatch.forEsItem))
      .foreach(potentialMatch => {
        os.println(
          potentialMatch.asJson.noSpaces
        )
      })

    os.flush()
  }

  private def exactTitleMatchQuery[T <: ScrapedItem: Codec](nonMatch: T) = {
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
//          builder.must(
//            // Boost exact release year matches
//            QueryBuilders
//              .boolQuery()
//              .should(
//                QueryBuilders
//                  .rangeQuery("release_date")
//                  .format("yyyy")
//                  .gte(s"${ry - 1}||/y")
//                  .lte(s"${ry + 1}||/y")
//              )
//              .should(
//                QueryBuilders
//                  .rangeQuery("release_date")
//                  .format("yyyy")
//                  .gte(s"${ry}||/y")
//                  .lte(s"${ry}||/y")
//                  .boost(1.5f)
//              )
//              .minimumShouldMatch(1)
//          )
          builder.filter(
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

  protected def elasticsearchExecutor: ElasticsearchExecutor

  private lazy val matcher = new ElasticsearchFallbackMatcher(
    elasticsearchExecutor,
    ElasticsearchFallbackMatcherOptions(
      requireTypeMatch,
      getClass.getSimpleName
    )
  )

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    matcher.handleNonMatches(
      args,
      nonMatches
    )
  }

  protected def flushElasticsearchFallbackMatchFile(): Unit = matcher.flush()
  protected def getElasticsearchFallbackMatchFile: File = matcher.outputFile
}
