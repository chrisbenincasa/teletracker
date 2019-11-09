package com.teletracker.common.config

import com.teletracker.common.util.json.ClassNameJsonSerializer
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.sql.Driver
import scala.concurrent.duration.FiniteDuration

case class TeletrackerConfig(
  cwd: String,
  mode: String = "multi user",
  db: DbConfig,
  elasticsearch: EsConfig,
  auth: AuthConfig,
  tmdb: TmdbConfig,
  env: String,
  async: AsyncConfig)

case class AuthConfig(
  admin: AdminConfig,
  cognito: CognitoConfig)

case class CognitoConfig(
  region: String,
  poolId: String)

case class AsyncConfig(taskQueue: QueueConfig)

case class QueueConfig(url: String)

case class AdminConfig(adminKeys: Set[String])

case class DbConfig(
  @JsonSerialize(using = classOf[ClassNameJsonSerializer])
  driver: Driver,
  url: String,
  user: String,
  password: String,
  cloudSqlInstance: Option[String])

case class EsConfig(
  hosts: List[EsHostConfig],
  creds: EsCredentials)

case class EsHostConfig(
  hostname: String,
  port: Int,
  scheme: String)

case class EsCredentials(
  user: Option[String],
  password: Option[String])

case class TmdbConfig(api_key: String)
