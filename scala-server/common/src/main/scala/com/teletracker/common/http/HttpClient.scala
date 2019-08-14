package com.teletracker.common.http

import scala.concurrent.Future

object HttpClientOptions {
  def default: HttpClientOptions = HttpClientOptions(useTls = false)

  def withTls: HttpClientOptions = HttpClientOptions(useTls = true)
}

case class HttpClientOptions(useTls: Boolean)

case class HttpRequest(
  path: String,
  params: List[(String, String)])

case class HttpResponse[T](content: T)

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
  options: HttpClientOptions)
    extends AutoCloseable {
  def get(request: HttpRequest): Future[HttpResponse[String]]
  def get(path: String): Future[HttpResponse[String]] =
    get(HttpRequest(path, Nil))

  def getBytes(request: HttpRequest): Future[HttpResponse[Array[Byte]]]
  def getBytes(path: String): Future[HttpResponse[Array[Byte]]] =
    getBytes(HttpRequest(path, Nil))
}
