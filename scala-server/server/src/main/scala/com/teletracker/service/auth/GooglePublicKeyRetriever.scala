package com.teletracker.service.auth

import com.teletracker.common.http.{BlockingHttp, HttpClient, HttpClientOptions}
import io.circe.parser._
import javax.inject.Inject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object GooglePublicKeyRetriever {
  final private val HOST = "www.googleapis.com"
  final private val PATH =
    "robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
  final private val url =
    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"

  final private val HttpDate = "EEE, dd MMM yyyy HH:mm:ss z"

  final private val lock = new Object
}

class GooglePublicKeyRetriever @Inject()(
  @BlockingHttp httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext) {
  import GooglePublicKeyRetriever._

  @volatile private var cachedCerts: CachedCerts = _

  private lazy val httpClient =
    httpClientFactory.create(HOST, HttpClientOptions.withTls)

  def getPublicCerts(): Future[Map[String, String]] = {
    val cached = GooglePublicKeyRetriever.lock.synchronized(cachedCerts)
    if (cached == null || cached.expiresAt.isBefore(OffsetDateTime.now())) {
      httpClient
        .get(PATH)
        .map(response => {

          val certs = parse(response.content)
            .flatMap(_.as[Map[String, String]])
            .fold(throw _, identity)

          val expiryTime = response.headers
            .get("cache-control")
            .flatMap(parseMaxAge)
            .orElse(response.headers.get("expires").flatMap(parseExpires))

          expiryTime
            .map(CachedCerts(certs, _))
            .foreach(cache => {
              GooglePublicKeyRetriever.lock.synchronized {
                cachedCerts = cache
              }
            })

          certs
        })
    } else {
      Future.successful(cachedCerts.certs)
    }
  }

  private def parseMaxAge(cacheControl: String) = {
    cacheControl
      .split(",")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.toLowerCase())
      .find(_.startsWith("max-age"))
      .flatMap(maxAge => {
        maxAge
          .split("=")
          .lastOption
          .flatMap(seconds => {
            Try(seconds.toInt).toOption.map(seconds => {
              OffsetDateTime.now().plusSeconds(seconds - 10)
            })
          })
      })
  }

  private def parseExpires(expires: String) = {
    Try {
      OffsetDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME)
    }.toOption
  }

  private case class CachedCerts(
    certs: Map[String, String],
    expiresAt: OffsetDateTime)
}
