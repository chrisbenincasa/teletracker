package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.Network
import javax.inject.Inject
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.{
  DeleteByQueryRequest,
  UpdateByQueryRequest
}
import org.elasticsearch.script.{Script, ScriptType}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class AvailabilityQueryHelper @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext) {
  def deleteAvailabilityForNetworks(
    networks: Set[StoredNetwork]
  ): Future[Unit] = {
    if (networks.isEmpty) {
      Future.unit
    } else {
      val updateByQuery = getDeleteAvailabilityForNetworksQuery(networks)

      elasticsearchExecutor.updateByQuery(updateByQuery).map(_ => {})
    }
  }

  def getDeleteAvailabilityForNetworksQuery(
    networks: Set[StoredNetwork]
  ): UpdateByQueryRequest = {
    val finalQuery = networks
      .foldLeft(QueryBuilders.boolQuery()) {
        case (query, network) =>
          query.should(
            QueryBuilders.termQuery("availability.network_id", network.id)
          )
      }
      .minimumShouldMatch(1)

    val query =
      QueryBuilders.nestedQuery("availability", finalQuery, ScoreMode.Avg)

    new UpdateByQueryRequest(
      teletrackerConfig.elasticsearch.items_index_name
    ).setQuery(
        query
      )
      .setScript(
        new Script(
          ScriptType.INLINE,
          "painless",
          "ctx._source.availability.removeIf(av -> params.network_ids.contains(av.network_id))",
          Map[String, AnyRef](
            "network_ids" -> networks.map(_.id).asJavaCollection
          ).asJava
        )
      )
  }
}
