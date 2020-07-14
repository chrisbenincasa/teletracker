package com.teletracker.common.http

import cats.data.EitherT._
import cats.effect.{Blocker, ContextShift, IO}
import io.circe.Json
import javax.inject.Inject
import org.http4s._
import org.http4s.circe._
import org.http4s.client.JavaNetClientBuilder
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import java.io.File

class BaseHttp4sClient @Inject()(
  blocker: Blocker
)(implicit cs: ContextShift[IO]) {
  def get(
    host: String,
    request: HttpRequest
  ): IO[HttpResponse[String]] = {
    getBasic[String](host, request, parseResponseDefault(_))
  }

  def getJson(
    host: String,
    request: HttpRequest
  ): IO[HttpResponse[Json]] = {
    getBasic[Json](host, request, parseResponseDefault(_))
  }

  def getBytes(
    host: String,
    request: HttpRequest
  ): IO[HttpResponse[Array[Byte]]] = {
    getBasic[Array[Byte]](host, request, parseResponseDefault(_))
  }

  def toFile(
    host: String,
    request: HttpRequest,
    destination: File,
    blocker: Blocker
  ): IO[HttpResponse[File]] = {
    val decoder = EntityDecoder.binFile[IO](destination, blocker)
    getBasic[File](
      host,
      request,
      parseResponseDefault(_)(decoder)
    )(decoder)
  }

  def getBasic[T](
    host: String,
    request: HttpRequest,
    makeResponse: Response[IO] => IO[HttpResponse[T]]
  )(implicit ed: EntityDecoder[IO, T]
  ): IO[HttpResponse[T]] = {
    val params = if (request.params.nonEmpty) request.params.foldLeft("?") {
      case (acc, (k, v)) => acc + s"&$k=$v"
    } else ""

    JavaNetClientBuilder[IO](blocker).resource.use { client =>
      Uri.fromString(s"$host/${request.path.stripPrefix("/")}$params") match {
        case Left(value) => IO.raiseError(value)
        case Right(uri) =>
          val builtRequest = buildRequest(uri, request)(ed)
          client.fetch(builtRequest)(makeResponse)
      }
    }
  }

  private def buildRequest[T](
    uri: Uri,
    request: HttpRequest
  )(implicit d: EntityDecoder[IO, T]
  ): Request[IO] = {
    val req = Request[IO](uri = uri)
    val withAccept = if (d.consumes.nonEmpty) {
      val m = d.consumes.toList
      req.putHeaders(
        Accept(
          MediaRangeAndQValue(m.head),
          m.tail.map(MediaRangeAndQValue(_)): _*
        )
      )
    } else req

    request.headers.foldLeft(withAccept) {
      case (r, (key, value)) => r.putHeaders(Header(key, value))
    }
  }

  private def parseResponseDefault[T](
    res: Response[IO]
  )(implicit d: EntityDecoder[IO, T]
  ): IO[HttpResponse[T]] = {
    catsDataBifunctorForEitherT[IO]
      .leftWiden[DecodeFailure, T, Throwable](d.decode(res, strict = false))
      .rethrowT
      .map(value => {
        HttpResponse[T](
          status = res.status.code,
          headers = res.headers.toList
            .map(header => header.name.value -> header.value)
            .toMap,
          content = value
        )
      })
  }
}
