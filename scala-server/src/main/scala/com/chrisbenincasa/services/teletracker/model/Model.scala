package com.chrisbenincasa.services.teletracker.model

import io.circe.{Encoder, Printer}
import io.circe.syntax._

object DataResponse {
  private val printer = Printer.spaces4.copy(dropNullValues = true)

  def complex[T](v: T)(implicit decoder: Encoder[T]): String = {
    printer.pretty(Map("data" -> v).asJson)
  }

  def standard[T](v: T): DataResponse[T] = DataResponse(v)
}

case class DataResponse[T](data: T)
