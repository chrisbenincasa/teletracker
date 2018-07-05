package com.chrisbenincasa.services.teletracker.external.tmdb

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import io.circe._
import io.circe.parser._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Future, Promise}

@Singleton
class TmdbClient @Inject()(config: TeletrackerConfig) {
  private val host = "api.themoviedb.org"

  private lazy val client = {
    Http.client.
      withTls(host).
      newService(s"$host:443")
  }

  def makeRequest[T](path: String, params: Seq[(String, String)] = Seq.empty)(implicit decoder: Decoder[T]): Future[T] = {
    val p = Promise[T]
    val query = Seq("api_key" -> config.tmdb.api_key) ++ params
    val req = Request(s"/3/${path.stripPrefix("/")}", query: _*)
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
