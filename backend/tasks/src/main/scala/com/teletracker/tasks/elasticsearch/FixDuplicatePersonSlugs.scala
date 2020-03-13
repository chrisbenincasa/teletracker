package com.teletracker.tasks.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  EsPerson,
  ItemUpdater,
  PersonLookup,
  PersonUpdater
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.{AsyncStream, Slug}
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.search.{
  SearchRequest,
  SearchResponse,
  SearchScrollRequest
}
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import org.elasticsearch.script.{Script, ScriptType}
import org.elasticsearch.search.Scroll
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.util.UUID
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.Try

private[elasticsearch] object Scripts {
  final val UpdateCastMemberSlugScriptSource =
    """
      |if (ctx._source.cast != null) {
      |  def member = ctx._source.cast.find(member -> member.id.equals(params.id));
      |  if (member != null) {
      |    member.slug = params.slug;
      |  }
      |}
      |""".stripMargin
}

class FixDuplicatePersonSlugs @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor,
  personUpdater: PersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs
    with ElasticsearchAccess {

  import Scripts._

  override protected def runInternal(args: Args): Unit = {
    val dupeSlug = args.value[String]("dupeSlug")
    val dryRun = args.valueOrDefault("dryRun", true)
    val limit = args.valueOrDefault("limit", 10)

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
        .fromFuture(getSlugCounts(None, Some(limit)))
        .flatMap(AsyncStream.fromSeq)
        .mapF(bucket => {
          fixPerson(bucket.getKeyAsString, bucket.getDocCount, dryRun)
        })
        .force
        .await()
    }
  }

  private def getSlugCounts(
    slugFilter: Option[String],
    limit: Option[Int] = None
  ) = {
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
      AggregationBuilders
        .terms("slug_agg")
        .field("slug")
        .minDocCount(2)
        .applyOptional(limit.filter(_ > 0))(_.size(_))

    val searchSource = new SearchSourceBuilder()
      .fetchSource(false)
      .query(query)
      .aggregation(aggs)

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

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private def fixPerson(
    slug: String,
    count: Long,
    dryRun: Boolean
  ) = {
    logger.info(s"Fixing slug (${slug}).")

    val query = QueryBuilders.termQuery("slug", slug)

    val request =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(
          new SearchSourceBuilder()
            .query(query)
            .size(100)
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
            .delayedMapF(100 millis, scheduler) {
              case (person, idx) =>
                val newSlug = person.slug.get.addSuffix(s"$idx")
                val newPerson = person.copy(
                  slug = Some(newSlug)
                )

                if (!dryRun) {
                  logger.info(
                    s"Updating person ${newPerson.id} with new slug: ${newSlug}"
                  )

                  personUpdater
                    .update(newPerson, refresh = true)
                    .flatMap(_ => fixCastAndCrew(newPerson.id, newSlug))
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
  ): Future[Unit] = {
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

    logger.info(
      s"Fixing cast and crew for person id ${personId} with new slug ${newSlug}"
    )

    elasticsearchExecutor
      .updateByQuery(getFixQuery("cast"))
      .flatMap(response => {
        logger
          .info(
            s"Fixed ${response.getUpdated} items' cast members for person ${personId}"
          )

        elasticsearchExecutor
          .updateByQuery(getFixQuery("crew"))
          .map(crewResponse => {
            logger.info(
              s"Fixed ${crewResponse.getUpdated} items' crew members for person ${personId}"
            )
          })
      })
  }
}

class FixDuplicateNumberedPersonSlugs @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor,
  personUpdater: PersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs
    with ElasticsearchAccess {
  import Scripts._

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val dupeSlug = args.value[String]("dupeSlug")
    val dryRun = args.valueOrDefault("dryRun", true)
    val limit = args.valueOrDefault("limit", 10)

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
        .fromFuture(getSlugCounts(None, Some(limit)))
        .flatMap(AsyncStream.fromSeq)
        .mapF(bucket => {
          fixPerson(bucket.getKeyAsString, bucket.getDocCount, dryRun)
        })
        .force
        .await()
    }
  }

  private def getSlugCounts(
    slugFilter: Option[String],
    limit: Option[Int] = None
  ) = {
    val query = QueryBuilders
      .boolQuery()
      .must(QueryBuilders.existsQuery("slug"))
      .must(QueryBuilders.regexpQuery("slug", ".*-[0-9]"))
      .mustNot(QueryBuilders.termQuery("slug", "-"))
      .mustNot(QueryBuilders.termQuery("slug", "--"))
      .mustNot(QueryBuilders.termQuery("slug", ""))
      .applyOptional(slugFilter)(
        (builder, slug) => builder.filter(QueryBuilders.termQuery("slug", slug))
      )

    val aggs =
      AggregationBuilders
        .terms("slug_agg")
        .field("slug")
        .minDocCount(2)
        .applyOptional(limit.filter(_ > 0))(_.size(_))

    val searchSource = new SearchSourceBuilder()
      .fetchSource(false)
      .query(query)
      .aggregation(aggs)

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
    logger.info(s"Fixing slug (${slug}).")

    val prefix = slug
      .split("-")
      .filter(part => Try(part.toInt).isFailure)
      .mkString("-")

    val query = QueryBuilders.regexpQuery("slug", s"$prefix(-[0-9])+")

    val request =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(
          new SearchSourceBuilder()
            .query(query)
            .size(100)
            .sort("popularity", SortOrder.DESC)
        )

    elasticsearchExecutor
      .search(request)
      .map(searchResponseToPeople)
      .flatMap(response => {
        val people = response.items

        if (people.size > 1) {
          val got = people.flatMap(_.slug).sortBy(_.value).mkString("\n")

          logger.info(s"Got:\n${got}\n")

          val (toFix, existing) =
            people.partition(_.slug.map(_.value).contains(slug))

          logger.info(s"Must fix:\n${toFix.flatMap(_.slug).mkString("\n")}\n")

          val nextSlugIndex = existing
            .flatMap(_.slug)
            .map(
              _.value.replaceAllLiterally(
                prefix + "-",
                ""
              )
            )
            .flatMap(x => Try(x.toInt).toOption)
            .max + 1

          val fixables =
            (nextSlugIndex to (nextSlugIndex + toFix.size)).zip(toFix).map {
              case (idx, person) => {
                person.copy(slug = Some(Slug.raw(prefix).addSuffix(s"$idx")))
              }
            }

          logger.info(s"Fixable:\n${fixables.flatMap(_.slug).mkString("\n")}")

//          var proceed = false
//          if (dryRun) {
//            logger.info("Proceed?")
//            val line = scala.io.StdIn.readLine()
//            println(line)
//            if (line.trim.equalsIgnoreCase("yes")) {
//              proceed = true
//            }
//          }

          AsyncStream
            .fromSeq(fixables.toList)
            .delayedMapF(100 millis, scheduler) {
              person =>
                if (!dryRun) {
                  logger.info(
                    s"Updating person ${person.id} with new slug: ${person.slug.get}"
                  )

                  personUpdater
                    .update(person, refresh = true)
                    .flatMap(_ => fixCastAndCrew(person.id, person.slug.get))
                } else {
                  Future
                    .successful {
                      logger.info(
                        s"Would've updated person ${person.id} with new slug: ${person.slug.get}"
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
  ): Future[Unit] = {
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

    logger.info(
      s"Fixing cast and crew for person id ${personId} with new slug ${newSlug}"
    )

    elasticsearchExecutor
      .updateByQuery(getFixQuery("cast"))
      .flatMap(response => {
        logger
          .info(
            s"Fixed ${response.getUpdated} items' cast members for person ${personId}"
          )

        elasticsearchExecutor
          .updateByQuery(getFixQuery("crew"))
          .map(crewResponse => {
            logger.info(
              s"Fixed ${crewResponse.getUpdated} items' crew members for person ${personId}"
            )
          })
      })
  }
}

class MigratePersonSlug @Inject()(
  personLookup: PersonLookup,
  personUpdater: PersonUpdater,
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs
    with ElasticsearchAccess {
  import Scripts._

  override protected def runInternal(args: Args): Unit = {
    val fromSlug = args.value[String]("from").map(Slug.raw)
    val fromId = args.value[UUID]("fromId")
    val toSlug = Slug.raw(args.valueOrThrow[String]("to"))

    if (fromSlug.isEmpty && fromId.isEmpty) {
      throw new IllegalArgumentException
    }

    val person = if (fromSlug.isDefined) {
      personLookup
        .lookupPersonBySlug(fromSlug.get, throwOnMultipleSlugs = true)
    } else {
      personLookup.lookupPerson(Left(fromId.get)).map(_.map(_._1))
    }

    person
      .flatMap {
        case None =>
          throw new IllegalArgumentException(
            s"No person found for slug ${fromSlug}"
          )
        case Some(value) =>
          for {
            _ <- personLookup.lookupPersonBySlug(toSlug).filter(_.isEmpty)
            _ <- personUpdater.update(value.copy(slug = Some(toSlug)))
            _ <- fixJoinField(value.id, toSlug, "cast")
            _ <- fixJoinField(value.id, toSlug, "crew")
          } yield {}
      }
      .await()
  }

  private def fixJoinField(
    personId: UUID,
    newSlug: Slug,
    field: String
  ) = {
    elasticsearchExecutor
      .updateByQuery(getFixQuery(personId, newSlug, field))
      .map(crewResponse => {
        logger.info(
          s"Fixed ${crewResponse.getUpdated} items' $field members for person ${personId}"
        )
      })
  }

  private def getFixQuery(
    personId: UUID,
    newSlug: Slug,
    field: String
  ) = {
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
}

class VerifyDupeSlugs @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs
    with ElasticsearchAccess {

  private val scheduler = Executors.newScheduledThreadPool(5)

  override protected def runInternal(args: Args): Unit = {
    val limit = args.valueOrDefault("limit", 10)
    val dryRun = args.valueOrDefault("dryRun", true)

    val query = QueryBuilders.regexpQuery("slug", ".*-[0-9]")

    val request =
      new SearchRequest(teletrackerConfig.elasticsearch.people_index_name)
        .source(new SearchSourceBuilder().query(query))
        .scroll(TimeValue.timeValueMinutes(5))

    val seenSingulars =
      new java.util.concurrent.ConcurrentHashMap[Slug, Slug]

    AsyncStream
      .fromFuture(elasticsearchExecutor.search(request))
      .flatMap(res => {
        scroll0(searchResponseToPeople(res).items, res, Option(res.getScrollId))
      })
      .map(person => (person.id, person.slug))
      .safeTake(limit)
      .flatMap {
        case (id, slug) =>
          val singularSlug =
            Slug.raw(slug.get.value.split("-").init.mkString("-"))

          val stream = AsyncStream.fromSeq(Seq(id -> slug))
          if (Option(seenSingulars.put(singularSlug, singularSlug)).isEmpty) {
            stream ++ AsyncStream
              .fromFuture(personLookup.lookupPersonBySlug(singularSlug))
              .flatMap {
                case Some(person) => AsyncStream.of(person.id -> person.slug)
                case None         => AsyncStream.empty
              }
          } else {
            stream
          }
      }
      .flatMap {
        case (id, slug) =>
          logger.info(s"Checking slug ${slug.get}")

          val query = QueryBuilders.nestedQuery(
            "cast",
            QueryBuilders.termQuery("cast.id", id.toString),
            ScoreMode.Avg
          )

          val updateFut = elasticsearchExecutor
            .search(
              new SearchRequest(
                teletrackerConfig.elasticsearch.items_index_name
              ).source(new SearchSourceBuilder().query(query))
            )
            .map(searchResponseToItems)
            .map(response => {
              val needsUpdate = response.items.flatMap(item => {
                val bustedMember = item.cast
                  .getOrElse(Nil)
                  .find(
                    member => member.id == id && member.slug != slug
                  )

                bustedMember match {
                  case Some(_) =>
                    logger.info(
                      s"Found slug mismatch for person ${id} on item ${item.id}"
                    )

                    val updatedCast = item.cast
                      .getOrElse(Nil)
                      .replaceWhere(_.id == id, _.copy(slug = slug))

                    Some(item.copy(cast = Some(updatedCast)))
                  case None => None
                }
              })

              AsyncStream
                .fromSeq(needsUpdate)
                .delayedMapF(150 millis, scheduler)(item => {
                  if (dryRun) {
                    Future.successful {
                      logger.info(
                        s"Would've updated item ${item.id} to fix cast member id ${id}"
                      )
                    }
                  } else {
                    itemUpdater.update(item)
                  }
                })
            })

          AsyncStream.fromFuture(updateFut.map(as => {
            val p = Promise[AsyncStream[Any]]
            scheduler.schedule(new Runnable {
              override def run(): Unit = p.success(as)
            }, 100, TimeUnit.MILLISECONDS)
            p.future
          }))
      }
      .force
      .await()
  }

  // We have more items if the current buffer is still full or if we have processed, in total, less than the number of hits.
  final private def hasNext(
    curr: List[EsPerson],
    lastResponse: SearchResponse,
    seen: Long
  ): Boolean = {
    curr.nonEmpty || seen < lastResponse.getHits.getTotalHits.value
  }

  // Continues the scroll given a scrollId
  final private def continue(
    scrollId: String,
    processed: Long
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[EsPerson] = {
    AsyncStream
      .fromFuture(
        elasticsearchExecutor.scroll(
          new SearchScrollRequest(scrollId)
            .scroll(TimeValue.timeValueMinutes(5))
        )
      )
      .flatMap(res => {
        scroll0(
          searchResponseToPeople(res).items,
          res,
          Option(res.getScrollId),
          processed
        )
      })
  }

  // Handle the next iteration of the scroll - either we use the buffered CUIDs from the last iteration or
  // continue the scroll query
  final private def scroll0(
    curr: List[EsPerson],
    lastResponse: SearchResponse,
    scrollId: Option[String] = None,
    seen: Long = 0
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[EsPerson] = {
    if (hasNext(curr, lastResponse, seen)) {
      // If the buffer is depleted, continue the scroll
      if (curr.isEmpty) {
        scrollId.map(continue(_, seen)).getOrElse(AsyncStream.empty)
      } else {
        // If we still have items in the buffer, pop off the head and lazily handle the tail
        AsyncStream.of(curr.head) ++ scroll0(
          curr.tail,
          lastResponse,
          scrollId,
          seen + 1
        )
      }
    } else {
      AsyncStream.empty
    }
  }
}
