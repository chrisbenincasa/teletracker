package com.teletracker.common.external.justwatch

import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import io.circe.Decoder
import io.circe.parser.decode
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JustWatchClient @Inject()(
  clientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val host = "apis.justwatch.com"
  private val client = {
    clientFactory.create(host, HttpClientOptions(useTls = true))
  }

  def makeRequest[T](
    path: String,
    params: Seq[(String, String)] = Seq.empty
  )(implicit decoder: Decoder[T]
  ): Future[T] = {
    client
      .get(
        HttpRequest(
          s"/${path.stripPrefix("/")}",
          params.toList
        )
      )
      .map(response => {
        val parsed = decode[T](response.content)
        decode[T](response.content) match {
          case Left(e) =>
            logger
              .error(s"Could not parse. Result string:\n${response.content}", e)
            throw e
          case Right(json) =>
            json
        }
      })
  }
}
