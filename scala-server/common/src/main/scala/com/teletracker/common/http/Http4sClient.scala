package com.teletracker.common.http

import cats.effect.{Blocker, ContextShift, IO}
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import org.http4s.{DecodeFailure, EntityDecoder, Header, Request, Response, Uri}
import org.http4s.client.JavaNetClientBuilder
import org.http4s.headers.{Accept, MediaRangeAndQValue}
import java.util.concurrent.Executors
import cats.implicits._
import cats.syntax.all._
import cats.data.EitherT._
import java.io.File
import scala.concurrent.{ExecutionContext, Future}

class Http4sClient @Inject()(
  @Assisted host: String,
  @Assisted options: HttpClientOptions
)(implicit executionContext: ExecutionContext)
    extends HttpClient(host, options) {

  private val blockingExecCtx =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  private val blocker: Blocker = Blocker.liftExecutionContext(blockingExecCtx)

  implicit private val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private val baseClient = new BaseHttp4sClient(blocker)

  private val fullHost = {
    if (options.useTls) {
      s"https://$host"
    } else {
      s"http://$host"
    }
  }

  override def get(request: HttpRequest): Future[HttpResponse[String]] = {
    baseClient.get(fullHost, request).unsafeToFuture()
  }

  override def getBytes(
    request: HttpRequest
  ): Future[HttpResponse[Array[Byte]]] = {
    baseClient.getBytes(fullHost, request).unsafeToFuture()
  }

  override def close(): Unit = {}

  private def buildRequest[T](
    uri: Uri,
    request: HttpRequest
  )(implicit d: EntityDecoder[IO, T]
  ) = {
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
          headers = res.headers.toList
            .map(header => header.name.value -> header.value)
            .toMap,
          content = value
        )
      })

  }
}
