package com.teletracker.common.db.access

import com.teletracker.common.db._
import com.teletracker.common.db.model.{
  DynamicListPersonRule,
  DynamicListTagRule,
  TrackedListRow,
  UserThingTagType
}
import com.teletracker.common.elasticsearch.EsItemTag.TagFormatter
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  ElasticsearchItemsResponse,
  EsItem,
  EsItemTag
}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.ListFilters
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.Aggregations
import org.elasticsearch.search.aggregations.bucket.terms.{
  DoubleTerms,
  Terms,
  TermsAggregationBuilder
}
import org.elasticsearch.search.aggregations.support.ValueType
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortOrder}
import java.time.LocalDate
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class ElasticsearchListBuilder @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def getRegularListItemCount(
    userId: String,
    list: TrackedListRow
  ): Future[Long] = {
    val listQuery =
      getRegularListQuery(userId, list, None, None, None, None)

    val countRequest = new CountRequest("items").source(listQuery)

    elasticsearchExecutor
      .count(countRequest)
      .map(response => response.getCount)
  }

  def getRegularListsCounts(
    userId: String,
    listIds: List[Int]
  ): Future[List[(Int, Long)]] = {
    val tag = EsItemTag.userScoped(userId, UserThingTagType.TrackedInList, None)
    val termQuery = QueryBuilders.termQuery("tags.tag", tag.tag)

    val agg =
      new TermsAggregationBuilder("regular_list_counts", ValueType.DOUBLE)
        .field("tags.value")

    val source = new SearchSourceBuilder()
      .aggregation(agg)
      .query(termQuery)
      .fetchSource(false)
    println(source)

    val searchRequest = new SearchRequest().source(source)

    elasticsearchExecutor
      .search(searchRequest)
      .map(response => {
        response.getAggregations.getAsMap.asScala
          .get("regular_list_counts")
          .collect {
            case agg: Terms =>
              agg.getBuckets.asScala.toList.map(bucket => {
                bucket.getKeyAsNumber.intValue() -> bucket.getDocCount
              })
          }
          .getOrElse(Nil)
      })
  }

  def getDynamicListCounts(
    userId: String,
    lists: List[TrackedListRow]
  ): Future[List[(Int, Long)]] = {
    Promise
      .fromTry(Try {
        require(lists.nonEmpty)
        require(lists.forall(_.isDynamic))
      })
      .future
      .flatMap(_ => {
        val queries = lists
          .map(getDynamicListQuery(userId, _, None, None, None, None))
          .map(_.fetchSource(false))
          .map(new SearchRequest().source(_))

        val mSearchRequest = new MultiSearchRequest()

        queries.foreach(mSearchRequest.add)

        elasticsearchExecutor
          .multiSearch(mSearchRequest)
          .map(searchResponse => {
            searchResponse.getResponses
              .zip(lists)
              .map {
                case (response, list) => {
                  list.id -> response.getResponse.getHits.getTotalHits.value
                }
              }
              .toList
          })
      })
  }

  def buildRegularList(
    userId: String,
    list: TrackedListRow,
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
      .search(new SearchRequest().source(sourceBuilder))
      .map(searchResponseToItems)
      .map(applyNextBookmark(_, bookmark, sortMode, isDynamic = false))

    val countFut = getRegularListItemCount(userId, list)

    for {
      items <- itemsFut
      count <- countFut
    } yield {
      items -> count
    }
  }

  def getDynamicListItemCount(
    userId: String,
    list: TrackedListRow
  ): Future[Long] = {
    val listQuery =
      getDynamicListQuery(userId, list, None, None, None, None)

    val countRequest = new CountRequest("items").source(listQuery)

    elasticsearchExecutor
      .count(countRequest)
      .map(response => response.getCount)
  }

  def buildDynamicList(
    userId: String,
    list: TrackedListRow,
    listFilters: Option[ListFilters],
    sortMode: SortMode = Popularity(),
    bookmark: Option[Bookmark] = None,
    limit: Int = 20
  ): Future[(ElasticsearchItemsResponse, Long)] = {
    val listQuery = getDynamicListQuery(
      userId,
      list,
      listFilters,
      Some(sortMode),
      bookmark,
      Some(limit)
    )

    val searchRequest = new SearchRequest("items").source(listQuery)

    val itemsFut = elasticsearchExecutor
      .search(searchRequest)
      .map(searchResponseToItems)
      .map(applyNextBookmark(_, bookmark, sortMode, isDynamic = true))

    val countFut = getDynamicListItemCount(userId, list)

    for {
      items <- itemsFut
      count <- countFut
    } yield {
      items -> count
    }
  }

  private def getRegularListQuery(
    userId: String,
    list: TrackedListRow,
    listFilters: Option[ListFilters],
    sortMode: Option[SortMode],
    bookmark: Option[Bookmark],
    limit: Option[Int]
  ) = {
    val baseBoolQuery = QueryBuilders
      .boolQuery()
      .filter(
        QueryBuilders
          .termQuery(
            "tags.tag",
            TagFormatter.format(userId, UserThingTagType.TrackedInList)
          )
      )
      .filter(QueryBuilders.termQuery("tags.value", list.id))
      .applyOptional(bookmark)(applyBookmark)

    new SearchSourceBuilder()
      .query(baseBoolQuery)
      .applyOptional(bookmark.map(_.sortMode).orElse(sortMode))(
        (builder, sort) => {
          builder
            .applyOptional(makeSort(sort))(_.sort(_))
            .sort(
              new FieldSortBuilder("id").order(SortOrder.ASC)
            )
        }
      )
      .applyOptional(limit)(_.size(_))
  }

  private def getDynamicListQuery(
    userId: String,
    dynamicList: TrackedListRow,
    listFilters: Option[ListFilters],
    sortMode: Option[SortMode],
    bookmark: Option[Bookmark],
    limit: Option[Int]
  ) = {
    require(dynamicList.isDynamic)
    require(dynamicList.rules.isDefined)

    val rules = dynamicList.rules.get

    val tagRules = rules.rules.collect {
      case tagRule: DynamicListTagRule => tagRule
    }

    val personRules = rules.rules.collect {
      case personRule: DynamicListPersonRule => personRule
    }

    val sourceBuilder = new SearchSourceBuilder()
    val baseBoolQuery = QueryBuilders.boolQuery()

    baseBoolQuery
      .applyIf(tagRules.nonEmpty)(
        builder =>
          tagRules.foldLeft(builder)((query, rule) => {
            query.filter(
              QueryBuilders
                .termQuery(
                  "tags.tag",
                  TagFormatter.format(userId, rule.tagType)
                )
            )
          })
      )
      .applyIf(personRules.nonEmpty)(
        builder =>
          personRules.foldLeft(builder)((query, rule) => {
            query.filter(
              QueryBuilders.nestedQuery(
                "cast",
                QueryBuilders.termQuery("cast.id", rule.personId.toString),
                ScoreMode.Avg
              )
            )
          })
      )
      .applyIf(listFilters.flatMap(_.itemTypes).exists(_.nonEmpty))(builder => {
        builder.filter(
          QueryBuilders.termsQuery(
            "type",
            listFilters
              .flatMap(_.itemTypes)
              .get
              .map(_.toString)
              .asJavaCollection
          )
        )
      })
      .applyOptional(listFilters.flatMap(_.genres).filter(_.nonEmpty))(
        (builder, genres) => {
          builder.filter(
            QueryBuilders.nestedQuery(
              "genres",
              QueryBuilders.termsQuery("genres.id", genres.asJavaCollection),
              ScoreMode.Avg
            )
          )
        }
      )
      .applyOptional(bookmark)(applyBookmark)

    sourceBuilder
      .query(baseBoolQuery)
      .applyOptional(bookmark.map(_.sortMode).orElse(sortMode))(
        (builder, sort) => {
          builder
            .applyOptional(makeSort(sort))(_.sort(_))
            .sort(
              new FieldSortBuilder("id").order(SortOrder.ASC)
            )
        }
      )
      .applyOptional(limit)(_.size(_))
  }

  private def applyNextBookmark(
    response: ElasticsearchItemsResponse,
    previousBookmark: Option[Bookmark],
    sortMode: SortMode,
    isDynamic: Boolean
  ) = {
    def getValueForBookmark(
      item: EsItem,
      actualSortMode: SortMode
    ): String = {
      actualSortMode match {
        case SearchScore(_) => throw new IllegalStateException("")
        case Popularity(_)  => item.popularity.getOrElse(0.0).toString
        case Recent(desc) =>
          item.release_date
            .getOrElse(if (desc) LocalDate.MIN else LocalDate.MAX)
            .toString
        case AddedTime(desc) =>
          // TODO implement correctly for direct lists
          getValueForBookmark(item, Recent(desc))

        case default @ DefaultForListType(_) =>
          getValueForBookmark(item, default.get(isDynamic))
      }
    }

    val nextBookmark = response.items.lastOption
      .map(
        item => {
          val value = getValueForBookmark(item, sortMode)

          val refinement = previousBookmark
            .filter(_.value == value)
            .map(_ => item.id.toString)

          Bookmark(
            Popularity(),
            item.popularity.getOrElse(0.0).toString,
            refinement
          )
        }
      )

    response.withBookmark(nextBookmark)
  }
}
