package com.teletracker.common.config

import com.teletracker.common.util.json.ClassNameJsonSerializer
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.sql.Driver
import scala.concurrent.duration.FiniteDuration

case class TeletrackerConfig(
  cwd: String,
  mode: String = "multi user",
  elasticsearch: EsConfig,
  auth: AuthConfig,
  tmdb: TmdbConfig,
  env: String,
  async: AsyncConfig,
  data: DataConfig)

case class AuthConfig(
  admin: AdminConfig,
  cognito: CognitoConfig)

case class CognitoConfig(
  region: String,
  poolId: String)

case class AsyncConfig(
  taskQueue: QueueConfig,
  esIngestQueue: QueueConfig)

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
  user_items_index_name: String)

case class EsHostConfig(
  hostname: String,
  port: Int,
  scheme: String)

case class EsCredentials(
  user: Option[String],
  password: Option[String])

case class TmdbConfig(api_key: String)

case class DataConfig(s3_bucket: String)
