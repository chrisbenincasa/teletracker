package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import io.circe.parser.decode
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.{
  BoolQueryBuilder,
  QueryBuilders,
  RangeQueryBuilder
}
import com.teletracker.common.util.Functions._
import io.circe.Decoder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import org.slf4j.LoggerFactory
import java.time.LocalDate
import scala.annotation.tailrec
import scala.reflect.ClassTag

trait ElasticsearchAccess {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def searchResponseToItems(
    response: SearchResponse
  ): ElasticsearchItemsResponse = {
    val hits = response.getHits

    ElasticsearchItemsResponse(
      decodeSearchResponse[EsItem](response),
      hits.getTotalHits.value
    )
  }

  protected def searchResponseToPeople(
    response: SearchResponse
  ): ElasticsearchPeopleResponse = {
    val hits = response.getHits

    ElasticsearchPeopleResponse(
      decodeSearchResponse[EsPerson](response),
      hits.getTotalHits.value
    )
  }

  protected def decodeSearchResponse[T: Decoder: ClassTag](
    response: SearchResponse
  ): List[T] = {
    val hits = response.getHits
    val decodedHits = hits.getHits.flatMap(hit => {
      decodeSourceString[T](hit.getSourceAsString)
    })

    decodedHits.toList
  }

  final protected def decodeSourceString[T](
    source: String
  )(implicit decoder: Decoder[T],
    ct: ClassTag[T]
  ): Option[T] = {
    decode[T](source) match {
      case Left(value) =>
        logger.error(
          s"Couldn't decode ${ct.runtimeClass.getSimpleName} from source string:\n${source}",
          value
        )
        None

      case Right(value) => Some(value)
    }
  }

  protected def removeAdultItems(
    builder: BoolQueryBuilder
  ): BoolQueryBuilder = {
    builder.filter(
      QueryBuilders
        .boolQuery()
        .should(
          QueryBuilders
            .boolQuery()
            .mustNot(QueryBuilders.existsQuery("adult"))
        )
        .should(
          QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery("adult", false))
        )
        .minimumShouldMatch(1)
    )
  }

  protected def itemTypeFilter(
    builder: BoolQueryBuilder,
    itemType: ThingType
  ) = {
    builder
      .should(QueryBuilders.termQuery("type", itemType.toString))
      .minimumShouldMatch(1)
  }

  protected def applyBookmark(
    builder: BoolQueryBuilder,
    bookmark: Bookmark
  ): BoolQueryBuilder = {
    def applyRange(
      rangeBuilder: RangeQueryBuilder,
      desc: Boolean,
      value: Any
    ): BoolQueryBuilder = {
      (desc, bookmark.valueRefinement) match {
        case (true, Some(_)) =>
          builder.filter(rangeBuilder.lte(value))

        case (true, _) =>
          builder.filter(rangeBuilder.lt(value))

        case (false, Some(_)) =>
          builder.filter(
            rangeBuilder
              .gte(value)
          )

        case (false, _) =>
          builder.filter(
            rangeBuilder
              .gt(value)
          )
      }
    }

    @scala.annotation.tailrec
    def applyForSortMode(sortMode: SortMode): BoolQueryBuilder = {
      sortMode match {
        case SearchScore(_) =>
          builder

        case Popularity(desc) =>
          val baseQuery = QueryBuilders
            .rangeQuery("popularity")

          val popularity = bookmark.value.toDouble

          applyRange(baseQuery, desc, popularity)

        case Recent(desc) =>
          val baseQuery = QueryBuilders
            .rangeQuery("release_date")
          val releaseDate = bookmark.value

          applyRange(baseQuery, desc, releaseDate)

        case AddedTime(desc)           => applyForSortMode(Recent(desc))
        case d @ DefaultForListType(_) => applyForSortMode(d.get(true))
      }
    }

    applyForSortMode(bookmark.sortMode)
      .applyIf(bookmark.valueRefinement.isDefined)(builder => {
        builder.filter(
          QueryBuilders.rangeQuery("id").gt(bookmark.valueRefinement.get)
        )
      })
  }

  @tailrec
  final protected def makeSort(sortMode: SortMode): Option[FieldSortBuilder] = {
    sortMode match {
      case SearchScore(_) => None

      case Popularity(desc) =>
        Some(
          new FieldSortBuilder("popularity")
            .order(if (desc) SortOrder.DESC else SortOrder.ASC)
            .missing("_last")
        )

      case Recent(desc) =>
        Some(
          new FieldSortBuilder("release_date")
            .order(if (desc) SortOrder.DESC else SortOrder.ASC)
            .missing("_last")
        )

      case AddedTime(desc)           => makeSort(Recent(desc)) // TODO: Wrong for lists
      case d @ DefaultForListType(_) => makeSort(d.get(true))
    }
  }
}
