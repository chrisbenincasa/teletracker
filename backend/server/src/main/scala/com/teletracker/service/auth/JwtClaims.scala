package com.teletracker.service.auth

import io.circe.generic.JsonCodec

@JsonCodec
case class JwtClaims(
  iss: Option[String],
  sub: Option[String],
  aud: Option[String],
  exp: Option[String],
  nbf: Option[String],
  iat: Option[String],
  jti: Option[String])
