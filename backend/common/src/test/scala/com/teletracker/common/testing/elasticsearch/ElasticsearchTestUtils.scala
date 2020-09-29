package com.teletracker.common.testing.elasticsearch

import com.teletracker.common.testing.docker.ElasticsearchContainer
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.xcontent.XContentType
import java.net.URL
import scala.io.Source
import scala.collection.JavaConverters._

object ElasticsearchTestUtils {
  def createIndex(
    container: ElasticsearchContainer,
    indexName: String,
    configLocation: URL
  ): String = {
    val source = Source.fromURL(configLocation)
    val config = source.mkString
    source.close()

    container.client
      .indices()
      .create(
        new CreateIndexRequest(indexName).source(config, XContentType.JSON),
        RequestOptions.DEFAULT
      )
      .index()
  }

  def createItemsIndex(container: ElasticsearchContainer): String = {
    val mapping = getClass.getClassLoader.getResource(
      "elasticsearch/migration/item_mapping_7_4.json"
    )

    createIndex(container, s"items_${System.currentTimeMillis()}", mapping)
  }

  def createAlias(
    container: ElasticsearchContainer,
    alias: String,
    target: String
  ) = {
    val indicesClient = container.client.indices()
    val existing = indicesClient.getAlias(
      new GetAliasesRequest(alias),
      RequestOptions.DEFAULT
    )

    val existingTargets = existing.getAliases.asScala.keys

    val baseRequest = new IndicesAliasesRequest()
      .addAliasAction(
        IndicesAliasesRequest.AliasActions.add().alias(alias).index(target)
      )

    val request = existingTargets.foldLeft(baseRequest)(
      (req, existing) =>
        req.addAliasAction(
          IndicesAliasesRequest.AliasActions
            .remove()
            .alias(alias)
            .index(existing)
        )
    )

    indicesClient.updateAliases(request, RequestOptions.DEFAULT).isAcknowledged
  }
}
