package com.teletracker.service.http

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.http.{
  HttpClient,
  HttpClientOptions,
  HttpRequest,
  HttpResponse
}
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import com.twitter.finagle.liveness.{
  FailureAccrualFactory,
  FailureAccrualPolicy
}
import com.twitter.finagle.service.Backoff
import javax.inject.Inject
import scala.concurrent.{Future, Promise}

class FinagleHttpClient @Inject()(
  @Assisted host: String,
  @Assisted options: HttpClientOptions)
    extends HttpClient(host, options) {
  private lazy val client = {
    Http.client
      .withTls(host)
      .withSessionQualifier
      .noFailureAccrual
      .withSessionQualifier
      .noFailFast
//      .configured(
//        FailureAccrualFactory.Param(
//          () =>
//            FailureAccrualPolicy
//              .consecutiveFailures(5, Backoff.const(10.seconds))
//        )
//      )
      .newService(s"$host:${if (options.useTls) "443" else "80"}")
  }

  override def get(request: HttpRequest): Future[HttpResponse[String]] = {
    val req = Request(request.path, request.params: _*)
    val promise = Promise[HttpResponse[String]]

    val resFut = client(req)

    resFut.onSuccess(res => {
      promise.success(HttpResponse(res.headerMap.toMap, res.contentString))
    })

    resFut.onFailure(promise.tryFailure)

    promise.future
  }

  override def getBytes(
    request: HttpRequest
  ): Future[HttpResponse[Array[Byte]]] = {
    val req = Request(request.path, request.params: _*)
    val promise = Promise[HttpResponse[Array[Byte]]]

    val resFut = client(req)

    resFut.onSuccess(res => {
      val bb = new Array[Byte](res.content.length)
      res.content.write(bb, 0)
      promise.success(HttpResponse(res.headerMap.toMap, bb))
    })

    resFut.onFailure(promise.tryFailure)

    promise.future
  }

  override def close(): Unit = {
    client.close()
  }
}