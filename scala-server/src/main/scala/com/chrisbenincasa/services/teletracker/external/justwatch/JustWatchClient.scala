package com.chrisbenincasa.services.teletracker.external.justwatch

import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import io.circe.Decoder
import io.circe.parser.decode
import javax.inject.Singleton
import scala.concurrent.{Future, Promise}

@Singleton
class JustWatchClient {
  private val host = "apis.justwatch.com"
  private lazy val client = {
    Http.client.
      withTls(host).
      withDecompression(true).
      newService(s"$host:443")
  }

  def makeRequest[T](path: String, params: Seq[(String, String)] = Seq.empty)(implicit decoder: Decoder[T]): Future[T] = {
    val p = Promise[T]
    val req = Request(s"/${path.stripPrefix("/")}", params: _*)
    val f = client(req)
    f.onSuccess(x => {
      decode[T](x.contentString) match {
        case Left(e) =>
          println(x.contentString)
          p.tryFailure(e)
        case Right(json) => p.trySuccess(json)
      }
    })
    f.onFailure(p.tryFailure)
    p.future
  }
}
