package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryBuilder,
  AvailabilityQueryHelper,
  ItemsScroller
}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.NetworkCache
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.common.bytes.BytesReference
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
import org.elasticsearch.index.mapper.DynamicTemplate.XContentFieldType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.{Script, ScriptType}
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class RemoveAvailabilitiesForNetwork @Inject()(
  networkCache: NetworkCache,
  availabilityQueryHelper: AvailabilityQueryHelper,
  elasticsearchExternalIdMappingStore: ElasticsearchExternalIdMappingStore,
  itemsScroller: ItemsScroller,
  esPotentialMatchItemStore: EsPotentialMatchItemStore
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val network = rawArgs.valueOrThrow[String]("network")
    val dryRun = rawArgs.valueOrDefault("dryRun", true)
    val deleteExternalIds = rawArgs.valueOrDefault("deleteExternalIds", true)
    val resetPotentialMatches =
      rawArgs.valueOrDefault("resetPotentialMatches", true)
    val catalogType = if (resetPotentialMatches) {
      rawArgs
        .valueOrThrow[ScrapeCatalogType]("catalogType")
    } else {
      ""
    }

    val allNetworks = networkCache.getAllNetworks().await()

    val storedNetwork = allNetworks
      .find(n => n.slug.value == network)
      .getOrElse(throw new IllegalArgumentException("Cannot find network"))

    val idsAndTypes: Set[(EsExternalId, ItemType)] = if (deleteExternalIds) {
      val externalSources =
        ExternalSource.fromString(storedNetwork.slug.value)

      itemsScroller
        .start(
          AvailabilityQueryBuilder.hasAvailabilityForNetworks(
            QueryBuilders.boolQuery(),
            Set(storedNetwork)
          )
        )
        .flatMapOption(item => {
          item.externalIdsGrouped
            .get(externalSources)
            .map(id => EsExternalId(externalSources, id) -> item.`type`)
        })
        .toList
        .await()
        .toSet
    } else {
      Set.empty
    }

    if (dryRun) {
      if (deleteExternalIds) {
        logger.info("Would've deleted the following external id mappings:")
        idsAndTypes.foreach(x => logger.info(s"$x"))
      }

      val query = availabilityQueryHelper.getDeleteAvailabilityForNetworksQuery(
        Set(storedNetwork)
      )

      val textQuery = XContentHelper
        .toXContent(query, XContentType.JSON, true)
      logger.info(
        s"Would've run this query to delete availabilities:\n${textQuery.utf8ToString()}"
      )
    } else {
      if (deleteExternalIds) {
        logger.info(s"Deleting ${idsAndTypes.size} external IDs")
        elasticsearchExternalIdMappingStore
          .unmapExternalIds(idsAndTypes)
          .await()
      }

      if (resetPotentialMatches) {
        logger.info("Resetting potential matches")

        val query = QueryBuilders.boolQuery
          .must(
            QueryBuilders.nestedQuery(
              "scraped",
              QueryBuilders.termQuery("scraped.type", catalogType),
              ScoreMode.Avg
            )
          )
          .must(QueryBuilders.termQuery("state", "matched"))

        val script = new Script(
          ScriptType.INLINE,
          "painless",
          "ctx._source.state = 'unmatched'",
          Map[String, AnyRef]().asJava
        )

        esPotentialMatchItemStore.updateByQuery(query, script).await()
      }
      logger.info("Deleting all availability.")

      availabilityQueryHelper
        .deleteAvailabilityForNetworks(Set(storedNetwork))
        .await()
    }
  }
}
