package com.teletracker.service.auth

import com.teletracker.common.http.{BlockingHttp, HttpClient, HttpClientOptions}
import io.circe.parser._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object GooglePublicKeyRetriever {
  final private val HOST = "www.googleapis.com"
  final private val PATH =
    "robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
  final private val url =
    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
}

class GooglePublicKeyRetriever @Inject()(
  @BlockingHttp httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext) {
  import GooglePublicKeyRetriever._

  private lazy val httpClient =
    httpClientFactory.create(HOST, HttpClientOptions.withTls)

  def getPublicCerts(): Future[Map[String, String]] = {
    httpClient
      .get(PATH)
      .map(response => {
        parse(response.content)
          .flatMap(_.as[Map[String, String]])
          .fold(throw _, identity)
      })
  }
}
