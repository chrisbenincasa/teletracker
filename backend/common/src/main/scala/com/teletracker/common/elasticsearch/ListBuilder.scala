package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.{
  AddedTime,
  Bookmark,
  DefaultForListType,
  Popularity,
  Recent,
  SearchScore,
  SortMode
}
import com.teletracker.common.db.model.{UserThingTagType}
import com.teletracker.common.util.ListFilters
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.{
  BoolQueryBuilder,
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
  SortOrder
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
    userId: String,
    list: StoredUserList
  ): Future[Long] = {
    val listQuery =
      getRegularListQuery(userId, list, None, None, None, None)

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
    listFilters: Option[ListFilters],
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    includeActions: Boolean = true,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long)] = {
    val sourceBuilder = getRegularListQuery(
      userId,
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
          val ids = response.items.flatMap(_.item_id).map(_.toString)

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
                  list.id,
                  bookmark,
                  sortMode
                )
              )
            })
        }

      })

    val countFut = getRegularListItemCount(userId, list)

    for {
      items <- itemsFut
      count <- countFut
    } yield {
      items -> count
    }
  }

  private def getRegularListQuery(
    userId: String,
    list: StoredUserList,
    listFilters: Option[ListFilters],
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
      .applyIf(listFilters.flatMap(_.itemTypes).exists(_.nonEmpty))(builder => {
        builder.filter(
          QueryBuilders.nestedQuery(
            "item",
            QueryBuilders.termsQuery(
              "type",
              listFilters
                .flatMap(_.itemTypes)
                .get
                .map(_.toString)
                .asJavaCollection
            ),
            ScoreMode.Avg
          )
        )
      })
      .applyOptional(listFilters.flatMap(_.genres).filter(_.nonEmpty))(
        (builder, genres) => {
          builder.filter(
            QueryBuilders.nestedQuery(
              "item",
              QueryBuilders.termsQuery(
                "item.genres.id",
                genres.asJavaCollection
              ),
              ScoreMode.Avg
            )
          )
        }
      )
      .applyOptional(bookmark)(applyBookmarkForRegularList(_, _, list))

    new SearchSourceBuilder()
      .query(baseBoolQuery)
      .applyOptional(bookmark.map(_.sortMode).orElse(sortMode))(
        (builder, sort) => {
          builder
            .applyOptional(makeSortForRegularList(sort, list.id))(_.sort(_))
            .sort(
              new FieldSortBuilder("item.id")
                .order(SortOrder.ASC)
                .setNestedSort(new NestedSortBuilder("item"))
            )
        }
      )
      .applyOptional(limit)(_.size(_))
  }

  @tailrec
  final private def makeSortForRegularList(
    sortMode: SortMode,
    listId: UUID
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
                      .termQuery("tags.string_value", listId.toString)
                  )
              )
            )
        )

      case d @ DefaultForListType(_) =>
        makeSortForRegularList(d.get(false), listId)
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

    @scala.annotation.tailrec
    def applyForSortMode(sortMode: SortMode): BoolQueryBuilder = {
      sortMode match {
        case SearchScore(_) =>
          builder

        case Popularity(desc) =>
          builder.filter(
            QueryBuilders
              .nestedQuery(
                "item",
                applyRange(
                  QueryBuilders.rangeQuery("item.popularity"),
                  desc,
                  bookmark.value.toDouble
                ),
                ScoreMode.Avg
              )
          )

        case Recent(desc) =>
          builder.filter(
            QueryBuilders.nestedQuery(
              "item",
              applyRange(
                QueryBuilders.rangeQuery("item.release_date"),
                desc,
                bookmark.value
              ).format("yyyy-MM-dd"),
              ScoreMode.Avg
            )
          )

        case AddedTime(desc) =>
          builder.filter(
            QueryBuilders.nestedQuery(
              "tags",
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
                ),
              ScoreMode.Avg
            )
          )

        case d @ DefaultForListType(_) =>
          applyForSortMode(d.get(list.isDynamic))
      }
    }

    applyForSortMode(bookmark.sortMode)
      .applyIf(bookmark.valueRefinement.isDefined)(builder => {
        builder.filter(
          QueryBuilders.nestedQuery(
            "item",
            QueryBuilders
              .rangeQuery("item.id")
              .gt(bookmark.valueRefinement.get),
            ScoreMode.Avg
          )
        )
      })
  }

  private def applyNextBookmarkRegularList(
    denormResponse: ElasticsearchUserItemsResponse,
    itemsResponse: ElasticsearchItemsResponse,
    listId: UUID,
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

    val bmValue = sortMode match {
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

      case t @ (AddedTime(_) | DefaultForListType(_)) =>
        denormResponse.items.lastOption
          .flatMap(
            userItem =>
              userItem.tags
                .find(
                  t =>
                    t.tag == UserThingTagType.TrackedInList.toString && t.string_value
                      .contains(listId.toString)
                )
                .flatMap(tag => {
                  userItem.item_id.map(itemId => {
                    itemId.toString -> tag.last_updated
                      .getOrElse(if (t.isDesc) LocalDate.MIN else LocalDate.MAX)
                      .toString
                  })
                })
          )

    }

    val nextBookmark = bmValue.map {
      case (itemId, value) =>
        val refinement = previousBookmark match {
          case Some(prev) if prev.value == value => Some(itemId.toString)
          case None                              => Some(itemId.toString)
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
