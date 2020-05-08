package com.teletracker.tasks.elasticsearch

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.db.model.UserThingTagType
import com.teletracker.common.elasticsearch.model.{EsItemTag, EsUserItem}
import com.teletracker.common.elasticsearch.{ElasticsearchExecutor, ItemUpdater}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import io.circe.parser._
import io.circe.syntax._
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import scala.concurrent.ExecutionContext

class MigrateToDynamoListIds @Inject()(
  listsDbAccess: ListsDbAccess,
  elasticsearchExecutor: ElasticsearchExecutor,
  itemUpdater: ItemUpdater,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val listIdFilter = args.value[Int]("listId")

    listsDbAccess
      .processAllLists(list => {
        if (!list.isDynamic) {
          list.legacyId
            .filter(id => listIdFilter.forall(_ == id))
            .foreach(legacyId => {
              println("Processing list " + legacyId)

              val baseBoolQuery = QueryBuilders
                .boolQuery()
                .filter(
                  QueryBuilders.nestedQuery(
                    "tags",
                    QueryBuilders
                      .termQuery(
                        "tags.tag",
                        UserThingTagType.TrackedInList.toString
                      ),
                    ScoreMode.Avg
                  )
                )
                .filter(
                  QueryBuilders.nestedQuery(
                    "tags",
                    QueryBuilders
                      .termQuery("tags.int_value", legacyId),
                    ScoreMode.Avg
                  )
                )

              val search = new SearchSourceBuilder()
                .query(baseBoolQuery)

              val foundUserItems = elasticsearchExecutor
                .search(
                  new SearchRequest(
                    teletrackerConfig.elasticsearch.user_items_index_name
                  ).source(search)
                )
                .map(response => {
                  val hits = response.getHits
                  val decodedHits = hits.getHits.flatMap(hit => {
                    decode[EsUserItem](hit.getSourceAsString).right.toOption
                  })

                  decodedHits.toList
                })
                .await()

              println(s"Found ${foundUserItems.size} items")

              if (foundUserItems.nonEmpty) {
                val newUserItems = foundUserItems
                  .map(userItem => {
                    val (trackedTag, otherTags) = userItem.tags
                      .partition(
                        _.tag == UserThingTagType.TrackedInList.toString
                      )
                    val newTrackedTags =
                      trackedTag
                        .map(_.copy(string_value = Some(list.id.toString)))

                    userItem.copy(
                      tags = newTrackedTags ++ otherTags
                    )
                  })

                val bulkRequest = new BulkRequest()

                newUserItems.foreach(newUserItem => {
                  bulkRequest.add(
                    new UpdateRequest(
                      teletrackerConfig.elasticsearch.user_items_index_name,
                      newUserItem.id
                    ).doc(newUserItem.asJson.noSpaces, XContentType.JSON)
                  )
                })

                elasticsearchExecutor.bulk(bulkRequest).await()

                for {
                  foundUserItem <- foundUserItems
                  if foundUserItem.user_id.isDefined && foundUserItem.item_id.isDefined
                } yield {
                  val tag = EsItemTag.userScoped(
                    foundUserItem.user_id.get,
                    UserThingTagType.TrackedInList,
                    Some(list.id.toString),
                    None
                  )

                  itemUpdater
                    .upsertItemTag(foundUserItem.item_id.get, tag, None)
                    .await()
                }

                println("saved user items")
              }
            })
        }
      })
      .await()
  }
}
