package com.teletracker.common.http

import com.google.inject.assistedinject.Assisted
import scala.concurrent.Future

case class HttpClientOptions(useTls: Boolean)

case class HttpRequest(
  path: String,
  params: List[(String, String)])

case class HttpResponse(content: String)

object HttpClient {
  trait Factory {
    def create(
      host: String,
      options: HttpClientOptions
    ): HttpClient
  }
}

abstract class HttpClient(
  host: String,
  options: HttpClientOptions) {
  def get(request: HttpRequest): Future[HttpResponse]
}
