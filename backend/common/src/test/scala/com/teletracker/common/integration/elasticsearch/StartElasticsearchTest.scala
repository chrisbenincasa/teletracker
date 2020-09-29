package com.teletracker.common.integration.elasticsearch

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.ElasticsearchExecutor
import com.teletracker.common.elasticsearch.model.EsItemRating
import com.teletracker.common.testing.docker.ElasticsearchContainer
import com.teletracker.common.testing.elasticsearch.ElasticsearchTestUtils
import com.teletracker.common.util.CaseClassImplicits
import com.teletracker.common.util.Futures._
import org.elasticsearch.action.index.IndexRequest
import io.circe.syntax._
import io.circe.parser._
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.script.{Script, ScriptType}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import java.time.Instant
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class StartElasticsearchTest
    extends AnyFlatSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with CaseClassImplicits {
  import com.teletracker.common.testing.scalacheck.ArbitraryEsItem._

  private var elasticsearch: ElasticsearchContainer = _

  override protected def beforeAll(): Unit = {
    elasticsearch = ElasticsearchContainer.create()
  }

  override protected def afterAll(): Unit = {
    if (elasticsearch ne null) {
      elasticsearch.close()
    }
  }

  it should "start elasticsearch" in {
    println("hey test!")

    val items = ElasticsearchTestUtils.createItemsIndex(elasticsearch)

    val acked =
      ElasticsearchTestUtils.createAlias(elasticsearch, "items_live", items)

    println(items)

    println(acked)

    val executor = new ElasticsearchExecutor(elasticsearch.client)

    val item = arbEsItem.arbitrary.sample.get

    println(s"generated doc: ${item.asJson.spaces2}")

    val rating = EsItemRating(
      ExternalSource.Imdb,
      9.8,
      Some(100),
      Some(8.9),
      Some(Instant.now())
    )

    executor
      .index(
        new IndexRequest("items_live")
          .create(true)
          .id(item.id.toString)
          .source(
            item
              .copy(
                ratings = Some(
                  List(
                    rating
                  )
                )
              )
              .asJson
              .noSpaces,
            XContentType.JSON
          )
      )
      .await()

    val result =
      executor.get(new GetRequest("items_live", item.id.toString)).await()

    println(
      s"retrieved doc: ${parse(result.getSourceAsString).right.get.spaces2}"
    )

    val removeClause =
      s"ctx._source.ratings.removeIf(r -> r.provider_id == ${ExternalSource.Imdb.ordinal()})"

    val scriptSource = s"""
       |if (ctx._source.ratings == null) {
       |   ctx._source.ratings = [params.rating];
       |} else {
       |  $removeClause;
       |   ctx._source.ratings.add(params.rating);
       |}
       |""".stripMargin

    val update = executor
      .update(
        new UpdateRequest("items_live", item.id.toString)
          .script(
            new Script(
              ScriptType.INLINE,
              "painless",
              scriptSource,
              Map[String, Object](
                "rating" -> rating
                  .copy(vote_average = 4.0, weighted_average = Some(2.1))
                  .mkMapAnyUnwrapOptions
                  .asJava
              ).asJava
            )
          )
          .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
          .fetchSource(true)
      )
      .await()

    println(
      s"updated doc: ${parse(update.getGetResult.sourceAsString()).right.get.spaces2}"
    )
  }
}
