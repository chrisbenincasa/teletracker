package com.teletracker.common.elasticsearch

import com.teletracker.common.db.dynamo.model.StoredNetwork
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilders}

object AvailabilityQueryBuilder {
  def hasAvailabilityForNetworks(
    builder: BoolQueryBuilder,
    networks: Set[StoredNetwork]
  ): BoolQueryBuilder = {
    require(networks.nonEmpty)

    builder.must(
      networks
        .foldLeft(QueryBuilders.boolQuery()) {
          case (nested, network) =>
            nested.should(
              QueryBuilders.nestedQuery(
                "availability",
                QueryBuilders.termQuery(s"availability.network_id", network.id),
                ScoreMode.Avg
              )
            )
        }
        .minimumShouldMatch(1)
    )
  }
}
