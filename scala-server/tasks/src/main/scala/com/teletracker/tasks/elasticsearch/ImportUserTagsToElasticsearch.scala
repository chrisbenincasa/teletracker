package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.access.UsersDbAccess
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsItem,
  EsItemTag
}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import io.circe.parser._
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import scala.concurrent.{ExecutionContext, Future}

class ImportUserTagsToElasticsearch @Inject()(
  usersDbAccess: UsersDbAccess,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    usersDbAccess
      .loopThroughAllUserActions() { batch =>
        val allFuts = batch.groupBy(_.thingId).toList.map {
          case (thingId, tags) =>
            val getRequest = new GetRequest("items", thingId.toString)

            elasticsearchExecutor
              .get(getRequest)
              .flatMap(response => {
                decode[EsItem](response.getSourceAsString) match {
                  case Left(value) => Future.unit
                  case Right(value) =>
                    val newTags =
                      tags
                        .map(
                          tag =>
                            EsItemTag.userScoped(
                              tag.userId,
                              tag.action,
                              tag.value
                            )
                        )
                        .distinct

                    val uniqueNewTags = newTags.map(_.tag).toSet

                    val finalTags = value.tags
                      .getOrElse(Nil)
                      .filterNot(tag => uniqueNewTags.contains(tag.tag)) ++ newTags.toList

                    val newDoc = value.copy(tags = Some(finalTags))

//                    new IndexRequest("items")
//                      .id(value.id.toString)
//                      .source(newDoc.asJson.noSpaces, XContentType.JSON)
                    val updateReq =
                      new UpdateRequest("items", value.id.toString)
                        .doc(newDoc.asJson.noSpaces, XContentType.JSON)

                    elasticsearchExecutor.update(updateReq).map(_ => {})
                }
              })
        }

        Future.sequence(allFuts).map(_ => {})
      }
      .await()
  }
}
