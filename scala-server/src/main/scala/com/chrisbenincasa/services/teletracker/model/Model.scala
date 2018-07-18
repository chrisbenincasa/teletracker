package com.chrisbenincasa.services.teletracker.model

import io.circe.Encoder
import io.circe.syntax._

object DataResponse {
  def complex[T](v: T)(implicit decoder: Encoder[T]): String = {
    Map("data" -> v).asJson.spaces4
  }

  def standard[T](v: T): DataResponse[T] = DataResponse(v)
}

case class DataResponse[T](data: T)
