package com.teletracker.service.config

import com.teletracker.service.util.json.ClassNameJsonSerializer
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.sql.Driver
import scala.concurrent.duration.FiniteDuration

case class TeletrackerConfig(
  cwd: String,
  mode: String = "multi user",
  db: DbConfig,
  auth: AuthConfig,
  tmdb: TmdbConfig
)

case class AuthConfig(
  jwt: JwtConfig
)

case class JwtConfig(
  issuer: String,
  audience: String,
  expiration: Option[FiniteDuration],
  secret: String
)

case class DbConfig(
  @JsonSerialize(using = classOf[ClassNameJsonSerializer])
  driver: Driver,
  url: String,
  user: String,
  password: String
)

case class TmdbConfig(
  api_key: String
)