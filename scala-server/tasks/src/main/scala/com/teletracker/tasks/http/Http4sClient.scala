package com.teletracker.tasks.http

import cats.effect.{Blocker, ContextShift, IO}
import com.google.inject.assistedinject.Assisted
import com.teletracker.common.http.{
  HttpClient,
  HttpClientOptions,
  HttpRequest,
  HttpResponse
}
import javax.inject.Inject
import org.http4s.{EntityDecoder, Uri}
import java.util.concurrent.Executors
//import org.http4s.client.blaze._
import org.http4s.client.JavaNetClientBuilder
import scala.concurrent.{ExecutionContext, Future}

class Http4sClient @Inject()(
  @Assisted host: String,
  @Assisted options: HttpClientOptions
)(implicit executionContext: ExecutionContext)
    extends HttpClient(host, options) {

  implicit private val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private val fullHost = {
    if (options.useTls) {
      s"https://$host"
    } else {
      s"http://$host"
    }
  }

  override def get(request: HttpRequest): Future[HttpResponse[String]] = {
    getBasic[String](request, HttpResponse(_))
  }

  override def getBytes(
    request: HttpRequest
  ): Future[HttpResponse[Array[Byte]]] = {
    getBasic[Array[Byte]](request, HttpResponse(_))
  }

  override def close(): Unit = {
    getClient.flatMap(_._2).unsafeRunSync()
  }

  private def getBasic[T](
    request: HttpRequest,
    makeResponse: T => HttpResponse[T]
  )(implicit ed: EntityDecoder[IO, T]
  ): Future[HttpResponse[T]] = {
    val params = if (request.params.nonEmpty) request.params.foldLeft("?") {
      case (acc, (k, v)) => acc + s"&$k=$v"
    } else ""

    Uri
      .fromString(s"$fullHost/${request.path.stripPrefix("/")}$params")
      .fold(
        Future.failed,
        uri => {
          getClient
            .map(_._1)
            .flatMap(_.expect[T](uri))
            .map(makeResponse)
            .unsafeToFuture()
        }
      )
  }

  private val blockingExecCtx =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  private val getClient = JavaNetClientBuilder[IO](
    Blocker.liftExecutionContext(blockingExecCtx)
  ).allocated
}
