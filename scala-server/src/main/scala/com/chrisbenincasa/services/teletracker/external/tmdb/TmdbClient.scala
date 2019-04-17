package com.chrisbenincasa.services.teletracker.external.tmdb

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.parser._
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}

@Singleton
class TmdbClient @Inject()(config: TeletrackerConfig) {
  private val logger = LoggerFactory.getLogger(getClass)

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
      val parsed = parse(x.contentString)
      parsed match {
        case Left(e) =>
          logger.error(s"Could not parse. Result string:\n${x.contentString}", e)
          p.tryFailure(e)
        case Right(s) =>
          s.as[T] match {
            case Left(_) =>  s.as[TmdbError].fold(p.tryFailure, p.tryFailure)
            case Right(json) => p.trySuccess(json)
          }
      }
    })
    f.onFailure(p.tryFailure)
    p.future
  }
}

@JsonCodec case class TmdbError(status_code: Int, status_message: String) extends Exception(status_message)