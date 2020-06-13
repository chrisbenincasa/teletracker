package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryBuilder,
  AvailabilityQueryHelper,
  ItemsScroller
}
import com.teletracker.common.elasticsearch.lookups.{
  DynamoElasticsearchExternalIdMapping,
  ElasticsearchExternalIdMappingStore
}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import org.elasticsearch.index.query.QueryBuilders
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

class DeleteExternalIdsForNetwork @Inject()(
  networkCache: NetworkCache,
  availabilityQueryHelper: AvailabilityQueryHelper,
  elasticsearchExternalIdMappingStore: DynamoElasticsearchExternalIdMapping,
  itemsScroller: ItemsScroller,
  esPotentialMatchItemStore: EsPotentialMatchItemStore
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val network = rawArgs.valueOrThrow[String]("network")
//
//    val allNetworks = networkCache.getAllNetworks().await()
//
//    val storedNetwork = allNetworks
//      .find(n => n.slug.value == network)
//      .getOrElse(throw new IllegalArgumentException("Cannot find network"))
//
//    val externalSources =
//      ExternalSource.fromString(storedNetwork.slug.value)
//    val idsAndTypes: Map[(EsExternalId, ItemType), UUID] = itemsScroller
//      .start(
//        AvailabilityQueryBuilder.hasAvailabilityForNetworks(
//          QueryBuilders.boolQuery(),
//          Set(storedNetwork)
//        )
//      )
//      .flatMapOption(item => {
//        item.externalIdsGrouped
//          .get(externalSources)
//          .map(
//            id => (EsExternalId(externalSources, id), item.`type`, item.id)
//          )
//      })
//      .toList
//      .await()
//      .map {
//        case (id, itemType, uuid) => (id, itemType) -> uuid
//      }
//      .toMap
//
//    println(s"Updating ${idsAndTypes.size} external ids")
//
//    elasticsearchExternalIdMappingStore.mapExternalIds(idsAndTypes).await()

    val buffer = new ListBuffer[(EsExternalId, ItemType)]
    elasticsearchExternalIdMappingStore
      .deleteExternalIdsPrefix(
        "hbo-go",
        item => {
          item.foreach(buffer += _)
        }
      )
      .flatMap(_ => {
        logger.info(s"Deleting ${buffer.size} keys")
        elasticsearchExternalIdMappingStore.unmapExternalIds(buffer.toSet)
      })
      .await()

  }
}
