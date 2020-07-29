package com.teletracker.common.config

import com.teletracker.common.config.core.api.ConfigWithPath

object TeletrackerConfig extends ConfigWithPath {
  override type ConfigType = TeletrackerConfig
  override val path: String = "teletracker"
}

case class TeletrackerConfig(
  cwd: String,
  mode: String = "multi user",
  elasticsearch: EsConfig,
  auth: AuthConfig,
  tmdb: TmdbConfig,
  env: String,
  async: AsyncConfig,
  data: DataConfig,
  dynamo: DynamoTablesConfig)

case class AuthConfig(
  admin: AdminConfig,
  cognito: CognitoConfig)

case class CognitoConfig(
  region: String,
  poolId: String)

case class AsyncConfig(
  taskQueue: QueueConfig,
  esIngestQueue: QueueConfig,
  esItemDenormalizationQueue: QueueConfig,
  esPersonDenormalizationQueue: QueueConfig,
  scrapeItemQueue: QueueConfig,
  amazonItemQueue: QueueConfig)

case class QueueConfig(
  url: String,
  message_group_id: Option[String],
  dlq: Option[DlqConfig])

// Has to be separate from QueueConfig or Ficus will blow up from recursive type
case class DlqConfig(
  url: String,
  message_group_id: Option[String])

case class AdminConfig(adminKeys: Set[String])

case class EsConfig(
  hosts: List[EsHostConfig],
  creds: EsCredentials,
  items_index_name: String,
  people_index_name: String,
  user_items_index_name: String,
  tasks_index_name: String,
  potential_matches_index_name: String,
  scraped_items_index_name: String)

case class EsHostConfig(
  hostname: String,
  port: Int,
  scheme: String)

case class EsCredentials(
  user: Option[String],
  password: Option[String])

case class TmdbConfig(api_key: String)

case class DataConfig(s3_bucket: String)

case class DynamoTablesConfig(
  scraped_items: DynamoTableConfig,
  crawls: DynamoTableConfig)

case class DynamoTableConfig(table_name: String)
