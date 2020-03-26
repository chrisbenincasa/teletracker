package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.elasticsearch.{ElasticsearchExecutor, EsItem}
import javax.inject.Inject
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.QueryBuilders
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Functions._
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import io.circe.parser._
import org.elasticsearch.action.bulk.{BulkAction, BulkRequest}
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class FixDuplicateSlugs @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val slug = args.value[String]("slug")
    val itemType = args.value[String]("type")
    val dryRun = args.valueOrDefault("dryRun", true)
    val limit = args.valueOrDefault("limit", -1)

    val query =
      QueryBuilders
        .boolQuery()
        .applyOptional(slug)(
          (builder, slug) => builder.must(QueryBuilders.termQuery("slug", slug))
        )
        .applyOptional(itemType)(
          (builder, itemType) =>
            builder.filter(QueryBuilders.termQuery("type", itemType))
        )
        .mustNot(QueryBuilders.prefixQuery("slug", "-"))

    val agg =
      AggregationBuilders
        .terms("dupe_slug")
        .field("slug")
        .minDocCount(2)
        .size(1000)

    val search = new SearchSourceBuilder().query(query).aggregation(agg)

    val response = elasticsearchExecutor
      .search(new SearchRequest("items").source(search))
      .await()

    response.getAggregations
      .get[Terms]("dupe_slug")
      .getBuckets
      .asScala
      .toList
      .safeTake(limit)
      .foreach(bucket => {
        if (bucket.getDocCount > 1) {
          val hits = findAllItemsWithSlug(
            bucket.getKeyAsString,
            bucket.getDocCount.toInt
          ).await()

          if (hits.size != bucket.getDocCount) {
            println(
              s"Aggregation thought there were ${bucket.getDocCount} docs but only found ${hits.size} in search"
            )
          } else {
            val bulkReq = new BulkRequest()

            val movieHits = hits
              .filter(_.`type` == ItemType.Movie)
            val showHits = hits
              .filter(_.`type` == ItemType.Show)

            if (dryRun) {
              println(
                s"${bucket.getKeyAsString}: Found ${movieHits.size} movies and ${showHits.size} shows"
              )
            } else {
              println(
                s"${bucket.getKeyAsString}: Fixing ${movieHits.size} movies and ${showHits.size} shows"
              )

              movieHits
                .drop(1)
                .zipWithIndex
                .foreach {
                  case (hit, idx) =>
                    val newSlug = hit.slug.map(_.addSuffix(s"${idx + 1}"))
                    bulkReq.add(
                      new UpdateRequest("items", hit.id.toString)
                        .doc(
                          Map[String, Object](
                            "slug" -> newSlug.map(_.value).orNull[String]
                          ).asJava
                        )
                    )
                }

              showHits
                .drop(1)
                .zipWithIndex
                .foreach {
                  case (hit, idx) =>
                    val newSlug = hit.slug.map(_.addSuffix(s"${idx + 1}"))
                    bulkReq.add(
                      new UpdateRequest("items", hit.id.toString)
                        .doc(
                          Map[String, Object](
                            "slug" -> newSlug.map(_.value).orNull[String]
                          ).asJava
                        )
                    )
                }

              if (bulkReq.numberOfActions() > 0) {
                elasticsearchExecutor.bulk(bulkReq).await()
              }
            }
          }
        }
      })
  }

  private def findAllItemsWithSlug(
    slug: String,
    limit: Int
  ) = {
    val query = QueryBuilders.termQuery("slug", slug)
    val search =
      new SearchSourceBuilder()
        .query(query)
        .sort("popularity", SortOrder.DESC)
        .size(limit)

    elasticsearchExecutor
      .search(new SearchRequest("items").source(search))
      .map(response => {
        response.getHits.getHits.toList.map(hit => {
          decode[EsItem](hit.getSourceAsString).right.get
        })
      })
  }
}
