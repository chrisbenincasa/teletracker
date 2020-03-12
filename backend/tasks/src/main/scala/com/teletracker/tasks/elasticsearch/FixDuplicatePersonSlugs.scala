package com.teletracker.tasks.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  PersonUpdater
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.{AsyncStream, Slug}
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import org.elasticsearch.script.{Script, ScriptType}
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FixDuplicatePersonSlugs @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor,
  personUpdater: PersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs
    with ElasticsearchAccess {

  final private val UpdateCastMemberSlugScriptSource =
    """
      |if (ctx._source.cast != null) {
      |  def member = ctx._source.cast.find(member -> member.id.equals(params.id));
      |  if (member != null) {
      |    member.slug = params.slug;
      |  }
      |}
      |""".stripMargin

  override protected def runInternal(args: Args): Unit = {
    val dupeSlug = args.value[String]("dupeSlug")
    val dryRun = args.valueOrDefault("dryRun", true)

    if (dupeSlug.isDefined) {
      AsyncStream
        .fromFuture(getSlugCounts(dupeSlug))
        .map(_.find(_.getKeyAsString == dupeSlug.get))
        .mapF {
          case Some(value) =>
            fixPerson(value.getKeyAsString, value.getDocCount, dryRun)

          case None =>
            Future.failed(
              new IllegalArgumentException(s"Slug not found: ${dupeSlug.get}")
            )
        }
        .force
        .await()
    } else {
      AsyncStream
        .fromFuture(getSlugCounts(None))
        .flatMap(AsyncStream.fromSeq)
        .mapF(bucket => {
          fixPerson(bucket.getKeyAsString, bucket.getDocCount, dryRun)
        })
        .force
        .await()
    }
  }

  private def getSlugCounts(slugFilter: Option[String]) = {
    val query = QueryBuilders
      .boolQuery()
      .must(QueryBuilders.existsQuery("slug"))
      .mustNot(QueryBuilders.termQuery("slug", "-"))
      .mustNot(QueryBuilders.termQuery("slug", "--"))
      .mustNot(QueryBuilders.termQuery("slug", ""))
      .applyOptional(slugFilter)(
        (builder, slug) => builder.filter(QueryBuilders.termQuery("slug", slug))
      )

    val aggs =
      AggregationBuilders.terms("slug_agg").field("slug").minDocCount(2)

    val searchSource = new SearchSourceBuilder().query(query).aggregation(aggs)

    logger.info(searchSource.toString())

    val search =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(searchSource)

    val aggResults = elasticsearchExecutor
      .search(search)
      .map(results => {
        val aggResult = results.getAggregations.get[Terms]("slug_agg")

        aggResult.getBuckets.asScala.toList
      })

    aggResults.foreach(buckets => {
      logger.info(s"Found ${buckets.size} slugs with >1 duplicates.")
    })

    aggResults
  }

  private def fixPerson(
    slug: String,
    count: Long,
    dryRun: Boolean
  ) = {
    val query = QueryBuilders.termQuery("slug", slug)

    val request =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(
          new SearchSourceBuilder()
            .query(query)
            .size(Math.min(Int.MaxValue, count).toInt)
            .sort("popularity", SortOrder.DESC)
        )

    elasticsearchExecutor
      .search(request)
      .map(searchResponseToPeople)
      .flatMap(response => {
        val people = response.items

        if (people.size > 1) {
          val peopleAndIdx = people.tail.zipWithIndex.map {
            case (person, idx) => person -> (idx + 2)
          }

          AsyncStream
            .fromSeq(peopleAndIdx)
            .mapF {
              case (person, idx) =>
                val newSlug = person.slug.get.addSuffix(s"$idx")
                val newPerson = person.copy(
                  slug = Some(newSlug)
                )

                if (!dryRun) {
                  personUpdater
                    .update(newPerson)
                    .map(_ => fixCastAndCrew(newPerson.id, newSlug))
                } else {
                  Future
                    .successful {
                      logger.info(
                        s"Would've updated person ${newPerson.id} with new slug: ${newSlug}"
                      )
                    }
                }
            }
            .force
        } else {
          logger.info(
            s"Only found 1 person with slug ${slug}: ${people.headOption.map(_.id)}"
          )
          Future.unit
        }
      })
  }

  private def fixCastAndCrew(
    personId: UUID,
    newSlug: Slug
  ): Future[BulkByScrollResponse] = {
    def getFixQuery(field: String) = {
      val query = QueryBuilders.nestedQuery(
        field,
        QueryBuilders.termQuery(s"$field.id", personId.toString),
        ScoreMode.Avg
      )

      val updateByQueryRequest = new UpdateByQueryRequest(
        teletrackerConfig.elasticsearch.items_index_name
      )

      updateByQueryRequest.setQuery(query)
      updateByQueryRequest.setScript(
        new Script(
          ScriptType.INLINE,
          "painless",
          UpdateCastMemberSlugScriptSource,
          Map[String, Object](
            "id" -> personId.toString,
            "slug" -> newSlug.value
          ).asJava
        )
      )
    }

    elasticsearchExecutor
      .updateByQuery(getFixQuery("cast"))
      .flatMap(_ => {
        elasticsearchExecutor.updateByQuery(getFixQuery("crew"))
      })
  }
}
