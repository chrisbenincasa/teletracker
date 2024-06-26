package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Rating,
  Recent,
  SearchScore,
  SortMode
}
import com.teletracker.common.db.model.{ExternalSource, UserThingTagType}
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsItemTag,
  ItemSearchParams
}
import com.teletracker.common.util.ListFilters
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.{
  BoolQueryBuilder,
  QueryBuilder,
  QueryBuilders,
  RangeQueryBuilder
}
import com.teletracker.common.util.Functions._
import org.elasticsearch.action.get.MultiGetRequest
import org.elasticsearch.search.aggregations.bucket.terms.{
  Terms,
  TermsAggregationBuilder
}
import org.elasticsearch.search.aggregations.support.ValueType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{
  FieldSortBuilder,
  NestedSortBuilder,
  SortOrder,
  SortMode => EsSortMode
}
import java.time.LocalDate
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class ListBuilder @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  def getRegularListItemCount(
    list: StoredUserList,
    listFilters: Option[ItemSearchParams]
  ): Future[Long] = {
    val listQuery =
      getRegularListQuery(list, listFilters, None, None, None)

    val countRequest = new CountRequest(
      teletrackerConfig.elasticsearch.user_items_index_name
    ).source(listQuery)

    elasticsearchExecutor
      .count(countRequest)
      .map(response => response.getCount)
  }

  def getRegularListsCounts(
    userId: String,
    listIds: List[UUID]
  ): Future[List[(UUID, Long)]] = {
    val tag =
      EsItemTag.TagFormatter.format(userId, UserThingTagType.TrackedInList)
    val termQuery = QueryBuilders.termQuery("tags.tag", tag)

    val agg =
      new TermsAggregationBuilder("regular_list_counts", ValueType.DOUBLE)
        .field("tags.string_value")

    val source = new SearchSourceBuilder()
      .aggregation(agg)
      .query(termQuery)
      .fetchSource(false)

    val searchRequest = new SearchRequest(
      teletrackerConfig.elasticsearch.items_index_name
    ).source(source)

    elasticsearchExecutor
      .search(searchRequest)
      .map(response => {
        response.getAggregations.getAsMap.asScala
          .get("regular_list_counts")
          .collect {
            case agg: Terms =>
              agg.getBuckets.asScala.toList.map(bucket => {
                UUID.fromString(bucket.getKeyAsString) -> bucket.getDocCount
              })
          }
          .getOrElse(Nil)
      })
  }

  def buildRegularList(
    userId: String,
    list: StoredUserList,
    listFilters: Option[ItemSearchParams],
    sortMode: SortMode,
    bookmark: Option[Bookmark] = None,
    includeActions: Boolean = true,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long)] = {
    val sourceBuilder = getRegularListQuery(
      list,
      listFilters,
      Some(sortMode),
      bookmark,
      Some(limit)
    )

    val itemsFut = elasticsearchExecutor
      .search(
        new SearchRequest(teletrackerConfig.elasticsearch.user_items_index_name)
          .source(sourceBuilder)
      )
      .map(searchResponseToUserItems)
      .flatMap(response => {
        if (response.items.isEmpty) {
          Future.successful(ElasticsearchItemsResponse.empty)
        } else {
          val ids = response.items.map(_.item_id).map(_.toString)

          val mGetBuilder = new MultiGetRequest()
          ids.map(
            mGetBuilder.add(teletrackerConfig.elasticsearch.items_index_name, _)
          )

          elasticsearchExecutor
            .multiGet(mGetBuilder)
            .map(
              response => {
                response.getResponses.toList.flatMap(response => {
                  Option(response.getResponse.getSourceAsString)
                    .flatMap(decodeSourceString[EsItem])
                })
              }
            )
            .map(items => {
              ElasticsearchItemsResponse(items, items.size, None).through(
                applyNextBookmarkRegularList(
                  response,
                  _,
                  list,
                  bookmark,
                  sortMode
                )
              )
            })
        }

      })

    val countFut = getRegularListItemCount(list, listFilters)

    for {
      items <- itemsFut
      count <- countFut
    } yield {
      items -> count
    }
  }

  private def getRegularListQuery(
    list: StoredUserList,
    listFilters: Option[ItemSearchParams],
    sortMode: Option[SortMode],
    bookmark: Option[Bookmark],
    limit: Option[Int]
  ) = {
    val baseBoolQuery = QueryBuilders
      .boolQuery()
      .filter(
        QueryBuilders.nestedQuery(
          "tags",
          QueryBuilders
            .termQuery("tags.tag", UserThingTagType.TrackedInList.toString),
          ScoreMode.Avg
        )
      )
      .filter(
        QueryBuilders.nestedQuery(
          "tags",
          QueryBuilders
            .termQuery("tags.string_value", list.id.toString),
          ScoreMode.Avg
        )
      )
      .through(builder => {
        val nestedQuery = QueryBuilders
          .boolQuery()
          .applyOptional(listFilters.flatMap(_.genres).filter(_.nonEmpty))(
            genresFilter
          )
          .applyOptional(listFilters.flatMap(_.itemTypes).filter(_.nonEmpty))(
            itemTypesFilter
          )
          .applyOptional(listFilters.flatMap(_.networks).filter(_.nonEmpty))(
            availabilityByNetworksOrNested
          )
          .applyOptional(listFilters.flatMap(_.imdbRating))(
            openRatingRangeFilter(
              _,
              ExternalSource.Imdb,
              _,
              path = "item.ratings"
            )
          )
          .applyOptional(listFilters.flatMap(_.releaseYear).filter(_.isFinite))(
            openDateRangeFilter
          )
          .applyOptional(
            listFilters
              .flatMap(_.peopleCredits)
              .filter(_.people.nonEmpty)
          )(
            peopleCreditSearchQuery
          )
          .applyOptional(listFilters.flatMap(_.tagFilters).filter(_.nonEmpty))(
            (builder, tags) => tags.foldLeft(builder)(itemTagFilter)
          )

        builder.filter(nestedItemQuery(nestedQuery))
      })
      .applyOptional(bookmark)(applyBookmarkForRegularList(_, _, list))

    val source = new SearchSourceBuilder()
      .query(baseBoolQuery)
      .applyOptional(bookmark.map(_.sortMode).orElse(sortMode))(
        (builder, sort) => {
          builder
            .applyOptional(makeSortForRegularList(sort, list))(_.sort(_))
            .sort(
              new FieldSortBuilder("item.id")
                .order(SortOrder.ASC)
                .setNestedSort(new NestedSortBuilder("item"))
            )
        }
      )
      .applyOptional(limit)(_.size(_))

    println(source)

    source
  }

  private def nestedItemQuery(query: QueryBuilder) = {
    QueryBuilders.nestedQuery(
      "item",
      query,
      ScoreMode.Avg
    )
  }

  @tailrec
  final private def makeSortForRegularList(
    sortMode: SortMode,
    list: StoredUserList
  ): Option[FieldSortBuilder] = {
    sortMode match {
      case SearchScore(_) => None

      case Popularity(desc) =>
        Some(
          new FieldSortBuilder("item.popularity")
            .order(if (desc) SortOrder.DESC else SortOrder.ASC)
            .missing("_last")
            .setNestedSort(new NestedSortBuilder("item"))
        )

      case Recent(desc) =>
        Some(
          new FieldSortBuilder("item.release_date")
            .order(if (desc) SortOrder.DESC else SortOrder.ASC)
            .missing("_last")
            .setNestedSort(new NestedSortBuilder("item"))
        )

      case Rating(isDesc, source) =>
        makeNestedRatingFieldSort(source, isDesc)

      case AddedTime(desc) =>
        Some(
          new FieldSortBuilder("tags.last_updated")
            .order(if (desc) SortOrder.DESC else SortOrder.ASC)
            .missing("_last")
            .setNestedSort(
              new NestedSortBuilder("tags").setFilter(
                QueryBuilders
                  .boolQuery()
                  .filter(
                    QueryBuilders
                      .termQuery(
                        "tags.tag",
                        UserThingTagType.TrackedInList.toString
                      )
                  )
                  .filter(
                    QueryBuilders
                      .termQuery("tags.string_value", list.id.toString)
                  )
              )
            )
        )

      case d @ DefaultForListType(_) =>
        makeSortForRegularList(d.get(isDynamic = false, list.userId), list)
    }
  }

  protected def makeNestedRatingFieldSort(
    source: ExternalSource,
    desc: Boolean
  ): Option[FieldSortBuilder] = {
    if (SupportedRatingSortSources.contains(source)) {
      val sort = new FieldSortBuilder("item.ratings.weighted_average")
        .sortMode(EsSortMode.AVG)
        .order(if (desc) SortOrder.DESC else SortOrder.ASC)
        .missing("_last")
        .setNestedSort(
          new NestedSortBuilder("item.ratings")
            .setFilter(
              QueryBuilders
                .termQuery("item.ratings.provider_id", source.ordinal())
            )
        )
      Some(sort)
    } else {
      throw new IllegalArgumentException(
        s"Sorting for ratings from ${source} is not supported"
      )
    }
  }

  private def applyBookmarkForRegularList(
    builder: BoolQueryBuilder,
    bookmark: Bookmark,
    list: StoredUserList
  ): BoolQueryBuilder = {
    def applyRange(
      rangeBuilder: RangeQueryBuilder,
      desc: Boolean,
      value: Any
    ): RangeQueryBuilder = {
      (desc, bookmark.valueRefinement) match {
        case (true, Some(_)) =>
          rangeBuilder.lte(value)

        case (true, _) =>
          rangeBuilder.lt(value)

        case (false, Some(_)) =>
          rangeBuilder
            .gte(value)

        case (false, _) =>
          rangeBuilder
            .gt(value)
      }
    }

    def applyValueRefinement(
      builder: BoolQueryBuilder,
      refinement: String
    ): BoolQueryBuilder = {
      builder.mustNot(
        QueryBuilders.termQuery("item.id", refinement)
      )
    }

    @scala.annotation.tailrec
    def applyForSortMode(sortMode: SortMode): BoolQueryBuilder = {
      sortMode match {
        case SearchScore(_) =>
          builder

        case Popularity(desc) =>
          QueryBuilders
            .boolQuery()
            .filter(
              applyRange(
                QueryBuilders.rangeQuery("item.popularity"),
                desc,
                bookmark.value.toDouble
              )
            )

        case Recent(desc) =>
          QueryBuilders
            .boolQuery()
            .filter(
              applyRange(
                QueryBuilders.rangeQuery("item.release_date"),
                desc,
                bookmark.value
              ).format("yyyy-MM-dd")
            )

        case Rating(isDesc, source) =>
          QueryBuilders
            .boolQuery()
            .filter(
              QueryBuilders.nestedQuery(
                "item.ratings",
                QueryBuilders
                  .boolQuery()
                  .filter(
                    applyRange(
                      QueryBuilders.rangeQuery("item.ratings.weighted_average"),
                      isDesc,
                      bookmark.value
                    )
                  )
                  .filter(
                    QueryBuilders
                      .termQuery("item.ratings.provider_id", source.ordinal())
                  ),
                ScoreMode.Avg
              )
            )

        case AddedTime(desc) =>
          QueryBuilders
            .boolQuery()
            .filter(
              applyRange(
                QueryBuilders.rangeQuery("tags.last_updated"),
                desc,
                bookmark.value
              )
            )
            .filter(
              QueryBuilders
                .termQuery(
                  "tags.tag",
                  UserThingTagType.TrackedInList.toString
                )
            )
            .filter(
              QueryBuilders
                .termQuery("tags.string_value", list.id.toString)
            )

        case d @ DefaultForListType(_) =>
          applyForSortMode(d.get(list.isDynamic, list.userId))
      }
    }

    builder.filter(
      nestedItemQuery(
        applyForSortMode(bookmark.sortMode)
          .applyOptional(bookmark.valueRefinement)(applyValueRefinement)
      )
    )
    applyForSortMode(bookmark.sortMode)
  }

  private def applyNextBookmarkRegularList(
    denormResponse: ElasticsearchUserItemsResponse,
    itemsResponse: ElasticsearchItemsResponse,
    list: StoredUserList,
    previousBookmark: Option[Bookmark],
    sortMode: SortMode
  ): ElasticsearchItemsResponse = {
    require(denormResponse.items.length == itemsResponse.items.length)
    // TODO: do this better
    require(
      denormResponse.items.lastOption
        .flatMap(_.id.split("_").lastOption) == itemsResponse.items.lastOption
        .map(_.id.toString)
    )

    @tailrec
    def getBookmarkValue(sort: SortMode): Option[(String, String)] = {
      sort match {
        case SearchScore(_) => throw new IllegalStateException("")
        case Popularity(_) =>
          itemsResponse.items.lastOption.map(
            item => item.id.toString -> item.popularity.getOrElse(0.0).toString
          )

        case Recent(desc) =>
          itemsResponse.items.lastOption.map(
            item =>
              item.id.toString -> item.release_date
                .getOrElse(if (desc) LocalDate.MIN else LocalDate.MAX)
                .toString
          )

        case Rating(desc, source) =>
          itemsResponse.items.lastOption.map(
            item =>
              item.id.toString -> item.ratingsGrouped
                .get(source)
                .flatMap(_.weighted_average)
                .getOrElse(if (desc) Double.MinValue else Double.MaxValue)
                .toString
          )

        case default @ DefaultForListType(_) =>
          getBookmarkValue(default.get(list.isDynamic, list.userId))

        case t @ AddedTime(_) =>
          denormResponse.items.lastOption
            .flatMap(
              userItem =>
                userItem.tags
                  .find(
                    t =>
                      t.tag == UserThingTagType.TrackedInList.toString && t.string_value
                        .contains(list.id.toString)
                  )
                  .map(tag => {
                    userItem.item_id.toString -> tag.last_updated
                      .getOrElse(if (t.isDesc) LocalDate.MIN else LocalDate.MAX)
                      .toString
                  })
            )

      }
    }

    val nextBookmark = getBookmarkValue(sortMode).map {
      case (itemId, value) =>
        val refinement = previousBookmark match {
          case Some(prev) if prev.value == value => Some(itemId)
          case None                              => Some(itemId)
          case _                                 => None
        }

        Bookmark(
          sortMode,
          value,
          refinement
        )
    }

    itemsResponse.withBookmark(nextBookmark)
  }
}
