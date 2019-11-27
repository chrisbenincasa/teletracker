package com.teletracker.tasks.scraper

import cats.syntax.group
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsItem
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.model.PotentialMatch
import io.circe.syntax._
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders
import org.elasticsearch.index.query.{Operator, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import scala.concurrent.Future

trait ElasticsearchFallbackMatcher[T <: ScrapedItem]
    extends ElasticsearchAccess {
  self: IngestJob[T] with IngestJobWithElasticsearch[T] =>

  protected def requireTypeMatch: Boolean = true

  protected def elasticsearchExecutor: ElasticsearchExecutor

  private val os = new PrintStream(
    new BufferedOutputStream(
      new FileOutputStream(
        new File(s"${today}-${getClass.getSimpleName}_potential-matches.txt")
      )
    )
  )

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    nonMatches
      .grouped(10)
      .toList
      .sequentially(group => {

        performExactTitleMatchQuery(group).flatMap(exactMatches => {
          val missing =
            group.filter(item => !exactMatches.exists(_._2.title == item.title))

          recordPotentialMatches(exactMatches)

          performFuzzyMatchQuery(missing).map(fuzzyMatches => {
            recordPotentialMatches(fuzzyMatches)

            (exactMatches ++ fuzzyMatches).map {
              case (esItem, scrapedItem) =>
                NonMatchResult(
                  scrapedItem,
                  scrapedItem,
                  esItem.id,
                  esItem.original_title.get
                )
            }
          })
        })
      })
      .map(_.flatten)
      .map(_ => {
        os.flush()
        // TODO: Actually return these
        Nil
      })
  }

  private def performExactTitleMatchQuery(group: Iterable[T]) = {
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
            for {
              esItem <- searchResponseToItems(response.getResponse).items.headOption
              if esItem.original_title.contains(item.title) || esItem.title.get
                .contains(item.title)
            } yield {
              esItem -> item
            }
        }
      })
      .map(_.flatten)
  }

  private def performFuzzyMatchQuery(group: Iterable[T]) = {
    val multiSearchRequest = new MultiSearchRequest()
    group
      .map(nonMatch => {
        val query = fuzzyQueryForItem(nonMatch)
        new SearchRequest("items")
          .source(new SearchSourceBuilder().query(query))
      })
      .foreach(multiSearchRequest.add)

    elasticsearchExecutor
      .multiSearch(
        multiSearchRequest
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

  private def recordPotentialMatches(
    potentialMatches: Iterable[(EsItem, T)]
  ) = {
    potentialMatches
      .map(Function.tupled(PotentialMatch.forEsItem))
      .foreach(potentialMatch => {
        os.println(
          potentialMatch.asJson.noSpaces
        )
      })

    os.flush()
  }

  private def exactTitleMatchQuery(nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .should(
        QueryBuilders
          .matchQuery("original_title", nonMatch.title)
          .operator(Operator.OR)
      )
      .should(
        QueryBuilders.matchQuery("title", nonMatch.title).operator(Operator.OR)
      )
      .minimumShouldMatch(1)
      .applyIf(requireTypeMatch && nonMatch.thingType.isDefined)(
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
              .gte(s"${ry}||/y")
              .lte(s"${ry - 1}||/y")
          )
      )
  }

  private def fuzzyQueryForItem(nonMatch: T) = {
//    QueryBuilders.functionScoreQuery(
//      ,
//      ScoreFunctionBuilders
//        .fieldValueFactorFunction("popularity")
//        .factor(1.2f)
//        .missing(0.8)
//        .modifier(FieldValueFactorFunction.Modifier.SQRT)
//    )
    QueryBuilders
      .boolQuery()
      .should(
        QueryBuilders
          .matchQuery("original_title", nonMatch.title)
          .operator(Operator.OR)
      )
      .should(
        QueryBuilders
          .matchQuery("title", nonMatch.title)
          .operator(Operator.OR)
      )
      .minimumShouldMatch(1)
      .applyIf(requireTypeMatch && nonMatch.thingType.isDefined)(
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
              .gte(s"${ry}||/y")
              .lte(s"${ry}||/y")
          )
      )
  }
}
