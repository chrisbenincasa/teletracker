package com.teletracker.service.auth

import io.jsonwebtoken.{Claims, JwsHeader, SigningKeyResolver}
import javax.inject.Inject
import java.security.Key

class JwkKeyResolver @Inject()(jwkProvider: FileJwkProvider)
    extends SigningKeyResolver {

  override def resolveSigningKey(
    header: (JwsHeader[T]) forSome { type T <: JwsHeader[T] },
    claims: Claims
  ): Key = {
    jwkProvider.get(header.getKeyId).getPublicKey
  }

  override def resolveSigningKey(
    header: (JwsHeader[T]) forSome { type T <: JwsHeader[T] },
    plaintext: String
  ): Key = {
    jwkProvider.get(header.getKeyId).getPublicKey
  }
}
