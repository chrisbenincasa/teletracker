package com.teletracker.common.elasticsearch.scraping

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.Bookmark
import com.teletracker.common.elasticsearch.model.{
  EsPotentialMatchItem,
  EsPotentialMatchState
}
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore.Sort
import com.teletracker.common.elasticsearch._
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.util.Functions._
import io.circe.Json
import io.circe.syntax._
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.{SearchRequest, SearchResponse}
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{
  FieldSortBuilder,
  NestedSortBuilder,
  SortOrder
}
import java.time.OffsetDateTime
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object EsPotentialMatchItemStore {
  sealed trait Sort {
    def repr: String
  }

  object Sort {
    def fromRepr(s: String): Sort = s.toLowerCase match {
      case IdSort.repr                  => IdSort
      case LastStateChangeTimeSort.repr => LastStateChangeTimeSort
      case PopularitySort.repr          => PopularitySort
      case _                            => throw new IllegalArgumentException(s"Unrecognized sort: ${s}")
    }

    case object IdSort extends Sort {
      val repr = "id"
    }

    case object LastStateChangeTimeSort extends Sort {
      val repr = "last_state_change"
    }

    case object PopularitySort extends Sort {
      val repr = "potential.popularity"
    }
  }
}

class EsPotentialMatchItemStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchCrud[String, EsPotentialMatchItem]
    with ElasticsearchAccess { self =>
  override protected val indexName: String =
    teletrackerConfig.elasticsearch.potential_matches_index_name

  def search(
    request: PotentialMatchItemSearch
  ): Future[EsPotentialMatchResponse] = {
    val actualSort = request.bookmark
      .map(_.sortType)
      .map(EsPotentialMatchItemStore.Sort.fromRepr)
      .orElse(request.sort.map(EsPotentialMatchItemStore.Sort.fromRepr))
      .getOrElse(EsPotentialMatchItemStore.Sort.IdSort)

    val isDesc = request.bookmark.map(_.desc).getOrElse(request.desc)

    def applyBookmark(
      queryBuilder: BoolQueryBuilder,
      bookmark: Bookmark
    ): BoolQueryBuilder = {
      val baseBuilder = QueryBuilders.rangeQuery(actualSort.repr)
      val rangeQuery = (bookmark.desc, bookmark.valueRefinement) match {
        case (true, Some(_))  => baseBuilder.lte(bookmark.value)
        case (true, _)        => baseBuilder.lt(bookmark.value)
        case (false, Some(_)) => baseBuilder.gte(bookmark.value)
        case (false, _)       => baseBuilder.gt(bookmark.value)
      }

      queryBuilder
        .applyOptional(bookmark.valueRefinement)(
          (builder, refinement) =>
            builder.mustNot(QueryBuilders.termQuery("id", refinement))
        )
        .through(builder => {
          actualSort match {
            case Sort.PopularitySort =>
              builder.filter(
                QueryBuilders
                  .nestedQuery("potential", rangeQuery, ScoreMode.Avg)
              )
            // Apply the filter directly to top-level fields
            case _ => builder.filter(rangeQuery)
          }
        })
    }

    def applySort(
      searchSourceBuilder: SearchSourceBuilder,
      bookmark: Option[Bookmark]
    ): SearchSourceBuilder = {
      actualSort match {
        case Sort.PopularitySort =>
          searchSourceBuilder.sort(
            new FieldSortBuilder(actualSort.repr)
              .setNestedSort(new NestedSortBuilder("potential"))
              .order(if (isDesc) SortOrder.DESC else SortOrder.ASC)
              .sortMode(org.elasticsearch.search.sort.SortMode.AVG)
          )
        // Apply top level sort directly
        case _ =>
          searchSourceBuilder.sort(
            actualSort.repr,
            if (isDesc) SortOrder.DESC else SortOrder.ASC
          )
      }
    }

    def genNextBookmark(lastItem: EsPotentialMatchItem): Bookmark = {
      val (value, refinement) = actualSort match {
        case Sort.IdSort => lastItem.id -> None
        case Sort.LastStateChangeTimeSort =>
          lastItem.last_state_change.toString -> Some(lastItem.id)
        case Sort.PopularitySort =>
          lastItem.potential.popularity.map(_.toString).getOrElse("") -> Some(
            lastItem.id
          )
      }

      Bookmark(
        actualSort.repr,
        desc = isDesc,
        value,
        refinement
      )
    }

    val query = buildSearchQuery(request)
      .applyOptional(request.bookmark)(applyBookmark)

    val countFut = count(request)

    val searchSource = new SearchSourceBuilder()
      .query(query)
      .size(request.limit)
      .through(applySort(_, bookmark = request.bookmark))

    println(searchSource)

    val searchRequest =
      new SearchRequest(indexName)
        .source(searchSource)

    val searchFut = elasticsearchExecutor
      .search(searchRequest)

    for {
      searchResponse <- searchFut
      countResponse <- countFut
    } yield {
      val items = decodeSearchResponse[EsPotentialMatchItem](searchResponse)
      val bookmark = items.lastOption.map(genNextBookmark)

      EsPotentialMatchResponse(
        items,
        countResponse,
        bookmark
      )
    }
  }

  def count(request: PotentialMatchItemSearch): Future[Long] = {
    elasticsearchExecutor
      .count(
        new CountRequest(indexName)
          .source(new SearchSourceBuilder().query(buildSearchQuery(request)))
      )
      .map(_.getCount)
  }

  private def buildSearchQuery(
    request: PotentialMatchItemSearch
  ): BoolQueryBuilder = {
    QueryBuilders
      .boolQuery()
      .must(
        QueryBuilders
          .termQuery(
            "state",
            request.state.getOrElse(EsPotentialMatchState.Unmatched).toString
          )
      )
      .applyOptional(request.scraperTypes.filter(_.nonEmpty))(
        (builder, typ) =>
          builder.must(
            QueryBuilders
              .nestedQuery(
                "scraped",
                QueryBuilders
                  .termsQuery("scraped.type", typ.map(_.toString).asJava),
                ScoreMode.Avg
              )
          )
      )
  }

  def updateState(
    id: String,
    state: EsPotentialMatchState
  ): Future[Unit] = {
    val update = Map(
      "state" -> state.getName,
      "last_state_change" -> OffsetDateTime.now().toString
    )

    val updateRequest = new UpdateRequest(indexName, id)
      .doc(update.asJson.noSpaces, XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

    elasticsearchExecutor.update(updateRequest).map(_ => {})
  }

  def partialUpdate(
    id: String,
    doc: Json
  ): Future[Unit] = {
    val updateRequest = new UpdateRequest(indexName, id)
      .doc(doc.deepDropNullValues.noSpaces, XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)

    elasticsearchExecutor.update(updateRequest).map(_ => {})
  }

  def scroller: Scroller[EsPotentialMatchItem] =
    new Scroller[EsPotentialMatchItem](elasticsearchExecutor) {
      override protected def indexName: String = self.indexName

      override protected def parseResponse(
        searchResponse: SearchResponse
      ): List[EsPotentialMatchItem] = {
        decodeSearchResponse[EsPotentialMatchItem](searchResponse)
      }
    }
}

case class PotentialMatchItemSearch(
  scraperTypes: Option[Set[ScrapeCatalogType]],
  state: Option[EsPotentialMatchState],
  limit: Int,
  bookmark: Option[Bookmark],
  sort: Option[String],
  desc: Boolean = true)
