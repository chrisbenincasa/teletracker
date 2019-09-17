package com.teletracker.common.auth.jwt

import com.google.inject.Inject
import com.teletracker.common.config.TeletrackerConfig
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import java.util.UUID

class JwtVendor @Inject()(config: TeletrackerConfig) {
  def vend(email: String): String = {
    val jwt = Jwts
      .builder()
      .setSubject(email)
      .setIssuer(config.auth.jwt.issuer)
      .setAudience(config.auth.jwt.audience)
      .setId(UUID.randomUUID().toString)
      .signWith(SignatureAlgorithm.HS256, config.auth.jwt.secret.getBytes()) // Change to HS512

    if (config.auth.jwt.expiration.isDefined) {
      jwt.setExpiration(
        new java.util.Date(
          System.currentTimeMillis() + config.auth.jwt.expiration.get.toMillis
        )
      )
    }

    jwt.compact()
  }
}