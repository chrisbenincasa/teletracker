package com.teletracker.common.http

import io.circe.Json
import scala.concurrent.Future

object HttpClientOptions {
  def default: HttpClientOptions = HttpClientOptions(useTls = false)

  def withTls: HttpClientOptions = HttpClientOptions(useTls = true)
}

case class HttpClientOptions(
  useTls: Boolean,
  poolSize: Int = 8)

case class HttpRequest(
  path: String,
  params: List[(String, String)] = Nil,
  headers: List[(String, String)] = Nil)

case class HttpResponse[T](
  status: Int,
  headers: Map[String, String],
  content: T)

object HttpClient {
  trait Factory {
    def create(
      host: String,
      options: HttpClientOptions
    ): HttpClient
  }
}

abstract class HttpClient extends AutoCloseable {
  def get(request: HttpRequest): Future[HttpResponse[String]]
  def get(path: String): Future[HttpResponse[String]] =
    get(HttpRequest(path, Nil))

  def getJson(request: HttpRequest): Future[HttpResponse[Json]]

  def getBytes(request: HttpRequest): Future[HttpResponse[Array[Byte]]]
  def getBytes(path: String): Future[HttpResponse[Array[Byte]]] =
    getBytes(HttpRequest(path, Nil))
}
