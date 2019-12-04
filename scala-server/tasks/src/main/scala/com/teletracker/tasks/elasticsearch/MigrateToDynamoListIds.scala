package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.db.model.UserThingTagType
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsUserItem,
  Indices
}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
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
  elasticsearchExecutor: ElasticsearchExecutor
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
                  new SearchRequest(Indices.UserItemIndex).source(search)
                )
                .map(response => {
                  val hits = response.getHits
                  val decodedHits = hits.getHits.flatMap(hit => {
                    decode[EsUserItem](hit.getSourceAsString).right.toOption
                  })

                  decodedHits.toList
                })
                .await()

              val newUserItems = foundUserItems
                .map(userItem => {
                  val (trackedTag, otherTags) = userItem.tags
                    .partition(_.tag == UserThingTagType.TrackedInList.toString)
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
                  new UpdateRequest(Indices.UserItemIndex, newUserItem.id)
                    .doc(newUserItem.asJson.noSpaces, XContentType.JSON)
                )
              })

              elasticsearchExecutor.bulk(bulkRequest).await()
            })
        }
      })
      .await()
  }
}
