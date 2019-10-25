package com.teletracker.tasks.scraper

import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsItem
}
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import io.circe.{Decoder, Encoder}
import org.elasticsearch.action.search.{
  MultiSearchRequest,
  MultiSearchRequestBuilder,
  SearchRequest
}
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction
import org.elasticsearch.index.query.functionscore.{
  FunctionScoreQueryBuilder,
  ScoreFunctionBuilders
}
import io.circe.syntax._
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import scala.concurrent.Future

trait ElasticsearchFallbackMatcher[T <: ScrapedItem]
    extends ElasticsearchAccess {
  self: IngestJob[T] with IngestJobWithElasticsearch[T] =>

  protected def elasticsearchExecutor: ElasticsearchExecutor

  private val os = new PrintStream(
    new BufferedOutputStream(
      new FileOutputStream(
        new File(s"${getClass.getSimpleName}_potentialMatches.txt")
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

          performFuzzyMatchQuery(missing).map(fuzzyMatches => {
            recordPotentialMatches(fuzzyMatches)

            exactMatches.map {
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

        val exactMatchMultiSearch = new MultiSearchRequest()
        group
          .map(nonMatch => {
            val query = exactTitleMatchQuery(nonMatch)
            new SearchRequest("items")
              .source(new SearchSourceBuilder().query(query))
          })
          .foreach(exactMatchMultiSearch.add)

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
      })
      .map(_.flatten)
      .map(potentialMatches => {
        potentialMatches.foreach {
          case (potentialEsItem, item) =>
            val stringToJson = Map(
              "original_title" -> potentialEsItem.original_title
                .getOrElse("")
                .asJson,
              "title" -> potentialEsItem.title.get.headOption
                .getOrElse("")
                .asJson,
              "release_date" -> potentialEsItem.release_date
                .map(_.toString)
                .getOrElse("")
                .asJson,
              "external_ids" -> potentialEsItem.external_ids
                .getOrElse(Nil)
                .asJson
            )
            os.println(
              Map(
                "potential" -> stringToJson.asJson,
                "scraped" -> item.asJson
              ).asJson.noSpaces
            )
        }

        os.flush()

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
    potentialMatches.foreach {
      case (potentialEsItem, item) =>
        val stringToJson = Map(
          "original_title" -> potentialEsItem.original_title
            .getOrElse("")
            .asJson,
          "title" -> potentialEsItem.title.get.headOption
            .getOrElse("")
            .asJson,
          "release_date" -> potentialEsItem.release_date
            .map(_.toString)
            .getOrElse("")
            .asJson,
          "external_ids" -> potentialEsItem.external_ids
            .getOrElse(Nil)
            .asJson
        )
        os.println(
          Map(
            "potential" -> stringToJson.asJson,
            "scraped" -> item.asJson
          ).asJson.noSpaces
        )
    }

    os.flush()
  }

  private def exactTitleMatchQuery(nonMatch: T) = {
    QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders.termQuery("original_title", nonMatch.title)
      )
      .must(
        QueryBuilders.termQuery("title", nonMatch.title)
      )
      .filter(
        QueryBuilders
          .termQuery("type", nonMatch.thingType.getName.toLowerCase())
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

  private def fuzzyQueryForItem(nonMatch: T) = {
    QueryBuilders.functionScoreQuery(
      QueryBuilders
        .boolQuery()
        .must(
          QueryBuilders.matchQuery("original_title", nonMatch.title)
        )
        .must(
          QueryBuilders.matchQuery("title", nonMatch.title)
        )
        .filter(
          QueryBuilders
            .termQuery("type", nonMatch.thingType.getName.toLowerCase())
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
        ),
      ScoreFunctionBuilders
        .fieldValueFactorFunction("popularity")
        .factor(1.2f)
        .missing(0.8)
        .modifier(FieldValueFactorFunction.Modifier.SQRT)
    )
  }
}
