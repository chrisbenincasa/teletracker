package com.teletracker.common.db.access

import com.teletracker.common.db.Bookmark
import com.teletracker.common.db.model.{
  DynamicListPersonRule,
  DynamicListRules,
  DynamicListTagRule,
  Genre,
  GenreType,
  Network,
  ThingType,
  TrackedListRow,
  UserThingTagType
}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsItem,
  PopularItemSearch
}
import com.teletracker.common.model.justwatch.PopularItem
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{ListFilters, Slug}
import org.scalatest.FlatSpec
import java.util.UUID
import io.circe.syntax._
import io.circe.parser._
import org.apache.http.HttpHost
import org.elasticsearch.client.{RestClient, RestHighLevelClient}
import scala.concurrent.ExecutionContext.Implicits.global

class DynamicListBuilderTest extends FlatSpec {
  val client = new RestHighLevelClient(
    RestClient.builder(
      new HttpHost("localhost", 9200, "http")
    )
  )

  val executor = new ElasticsearchExecutor(client)

  "dynamic lists" should "work" in {
    val builder = new ElasticsearchListBuilder(executor)
    val list = TrackedListRow(
      1,
      "Default",
      false,
      false,
      "1",
      isDynamic = true,
      rules = Some(
        DynamicListRules(
          List(
            DynamicListPersonRule(
              personId = UUID.fromString("4811f8ca-f7ec-4857-8f75-d877c0c43448")
            )
            //                DynamicListTagRule(
            //                  tagType = UserThingTagType.Watched,
            //                  value = None,
            //                  isPresent = Some(true)
            //                )
          )
        )
      )
    )

    val result = builder
      .buildDynamicList(
        "123",
        list,
        Some(ListFilters(Some(Set(ThingType.Movie)), Some(Set(85))))
      )
      .await()

    val count = builder.getDynamicListItemCount("123", list).await()

    println(count)

    result.items.foreach(hit => {
      println(hit)
    })
  }

  "genres" should "work" in {
    val popularItemSearch = new PopularItemSearch(executor)
    val search = popularItemSearch
      .getPopularItems(
//        Some(
//          Genre(
//            Some(83),
//            "Action",
//            Slug.raw("action"),
//            List(GenreType.Movie, GenreType.Tv)
//          )
//        ),
        genre = None,
//        Some(Set(ThingType.Movie)),
        networks = Set(
          Network(Some(1), "Netflix", Slug.raw("netflix"), "nflx", None, None)
        ),
        itemTypes = None,
        limit = 20,
        bookmark = None
      )
      .await()

    search.items.foreach(hit => {
      println(hit.title.get.head)
    })
  }
}
