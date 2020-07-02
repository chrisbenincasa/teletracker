package com.teletracker.tasks.scraper.loaders

import com.teletracker.common.db.model.SupportedNetwork
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryBuilder,
  ItemsScroller
}
import com.teletracker.common.util.NetworkCache
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchAvailabilityItemLoaderArgs(
  override val supportedNetworks: Set[SupportedNetwork])
    extends AvailabilityItemLoaderArgs

class ElasticsearchAvailabilityItemLoader @Inject()(
  networkCache: NetworkCache,
  itemsScroller: ItemsScroller
)(implicit executionContext: ExecutionContext)
    extends AvailabilityItemLoader[
      EsItem,
      ElasticsearchAvailabilityItemLoaderArgs
    ] {

  override def loadImpl(
    args: ElasticsearchAvailabilityItemLoaderArgs
  ): Future[List[EsItem]] = synchronized {
    networkCache
      .getAllNetworks()
      .map(networks => {
        val foundNetworks = networks.collect {
          case network
              if network.supportedNetwork.isDefined && args.supportedNetworks
                .contains(
                  network.supportedNetwork.get
                ) =>
            network
        }.toSet

        if (args.supportedNetworks
              .diff(foundNetworks.flatMap(_.supportedNetwork))
              .nonEmpty) {
          throw new IllegalStateException(
            s"""Could not find all networks "${args.supportedNetworks}" network from datastore"""
          )
        }

        foundNetworks
      })
      .flatMap(networks => {
        itemsScroller
          .start(
            AvailabilityQueryBuilder.hasAvailabilityForNetworks(
              QueryBuilders.boolQuery(),
              networks
            )
          )
          .toList
      })
  }
}
