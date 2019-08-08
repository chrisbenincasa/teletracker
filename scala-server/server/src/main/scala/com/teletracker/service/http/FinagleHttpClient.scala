package com.teletracker.service.http

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.http.{
  HttpClient,
  HttpClientOptions,
  HttpRequest,
  HttpResponse
}
import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import javax.inject.Inject
import scala.concurrent.{Future, Promise}

class FinagleHttpClient @Inject()(
  @Assisted host: String,
  @Assisted options: HttpClientOptions)
    extends HttpClient(host, options) {
  private lazy val client = {
    Http.client
      .withTls(host)
      .newService(s"$host:${if (options.useTls) "443" else "80"}")
  }

  override def get(request: HttpRequest): Future[HttpResponse] = {
    val req = Request(request.path, request.params: _*)
    val promise = Promise[HttpResponse]

    val resFut = client(req)

    resFut.onSuccess(res => {
      promise.success(HttpResponse(res.contentString))
    })

    resFut.onFailure(promise.tryFailure)

    promise.future
  }
}
