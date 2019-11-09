package com.teletracker.service.auth

import com.auth0.jwk.{Jwk, JwkProvider}
import com.google.common.cache.{Cache, CacheBuilder}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.http.{BlockingHttp, HttpClient, HttpClientOptions}
import com.teletracker.service.auth.GooglePublicKeyRetriever.HOST
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.parser._
import javax.inject.Inject
import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object AsyncJwkProvider {
  final private def getJwkLocation(
    region: String,
    poolId: String
  ) =
    s"https://cognito-idp.${region}.amazonaws.com/${poolId}"

  final private val JwkPath = ".well-known/jwks.json"
}

class AsyncJwkProvider @Inject()(
  teletrackerConfig: TeletrackerConfig,
  @BlockingHttp httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext) {

  import AsyncJwkProvider._

  private val cache: Cache[String, List[Jwk]] = CacheBuilder
    .newBuilder()
    .expireAfterWrite(Duration.of(1, ChronoUnit.HOURS))
    .build()

  private lazy val httpClient =
    httpClientFactory.create(
      getJwkLocation(
        teletrackerConfig.auth.cognito.region,
        teletrackerConfig.auth.cognito.poolId
      ),
      HttpClientOptions.withTls
    )

  def getJwks(): Future[List[Jwk]] = {
    Option(cache.getIfPresent("jwks"))
      .map(
        Future.successful
      )
      .getOrElse {
        httpClient
          .get(JwkPath)
          .map(response => {
            decode[AwsJwkResponse](response.content) match {
              case Left(value) =>
                throw value

              case Right(value) =>
                val jwks = value.keys.map(key => {
                  new Jwk(
                    key.kid,
                    key.kty,
                    key.alg,
                    key.use,
                    List.empty[String].asJava,
                    null,
                    null,
                    null,
                    null
                  )
                })

                cache.put("jwks", jwks)

                jwks
            }
          })
      }
  }
}

@JsonCodec
case class AwsJwkResponse(keys: List[AwsJwk])
@JsonCodec
case class AwsJwk(
  alg: String,
  e: String,
  kid: String,
  kty: String,
  n: String,
  use: String)
