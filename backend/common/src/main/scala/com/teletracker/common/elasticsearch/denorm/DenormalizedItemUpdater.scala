package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsUserDenormalizedItem
}
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchExecutor,
  ItemLookup
}
import com.teletracker.common.util.json.IdentityFolder
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import org.elasticsearch.script.{Script, ScriptType}
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object DenormalizedItemUpdater {
  final private val UpdateDenormalizedItemScriptSource =
    """ctx._source.item = params.item"""

  final private def UpdateDenormalizedItemScript(
    item: EsUserDenormalizedItem
  ) = {
    new Script(
      ScriptType.INLINE,
      "painless",
      UpdateDenormalizedItemScriptSource,
      Map[String, Object]("tag" -> itemAsMap(item)).asJava
    )
  }

  private def itemAsMap(
    item: EsUserDenormalizedItem
  ): java.util.Map[String, Any] = {
    item.asJson.asObject.get.toMap.mapValues(_.foldWith(IdentityFolder)).asJava
  }
}

class DenormalizedItemUpdater @Inject()(
  teletrackerConfig: TeletrackerConfig,
  itemLookup: ItemLookup,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  import DenormalizedItemUpdater._

  def updateUserItems(itemId: UUID): Future[BulkByScrollResponse] = {
    itemLookup
      .lookupItem(
        Left(itemId),
        None,
        shouldMateralizeCredits = false,
        shouldMaterializeRecommendations = false
      )
      .flatMap {
        case Some(value) =>
          updateUserItems(value.rawItem)
        case None =>
          Future.failed(
            new IllegalArgumentException(s"Item with ID = ${itemId} not found.")
          )
      }
  }

  def updateUserItems(item: EsItem): Future[BulkByScrollResponse] = {
    val matchingItems =
      QueryBuilders.termQuery("item_id", item.id.toString)

    val updateByQueryRequest = new UpdateByQueryRequest(
      teletrackerConfig.elasticsearch.user_items_index_name
    )

    updateByQueryRequest.setQuery(matchingItems)
    updateByQueryRequest.setScript(
      UpdateDenormalizedItemScript(item.toDenormalizedUserItem)
    )
    updateByQueryRequest.setConflicts("proceed")
    updateByQueryRequest.setRequestsPerSecond(25)

    elasticsearchExecutor.updateByQuery(updateByQueryRequest)
  }
}
