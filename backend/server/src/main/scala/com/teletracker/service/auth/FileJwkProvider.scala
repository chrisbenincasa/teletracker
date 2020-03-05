package com.teletracker.service.auth

import com.auth0.jwk.{Jwk, JwkProvider}
import javax.inject.{Inject, Singleton}
import io.circe.parser._
import scala.io.Source
import scala.collection.JavaConverters._

@Singleton
class FileJwkProvider @Inject()() extends JwkProvider {
  private val jwks = {
    decode[AwsJwkResponse](
      Source
        .fromInputStream(
          getClass.getClassLoader.getResourceAsStream("jwks.json")
        )
        .getLines()
        .mkString("")
    ).right
      .getOrElse {
        throw new RuntimeException(
          "Could not properly decode jwks.json from resources."
        )
      }
      .keys
      .map(key => {
        new Jwk(
          key.kid,
          key.kty,
          key.alg,
          key.use,
          List.empty[String].asJava,
          null,
          null,
          null,
          Map[String, Object](
            "n" -> key.n,
            "e" -> key.e
          ).asJava
        )
      })
  }

  override def get(keyId: String): Jwk = {
    jwks
      .find(_.getId == keyId)
      .getOrElse(
        throw new IllegalArgumentException(
          s"Could not find JWK with id = ${keyId}"
        )
      )
  }
}
