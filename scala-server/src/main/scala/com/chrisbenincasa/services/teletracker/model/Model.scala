package com.chrisbenincasa.services.teletracker.model

import io.circe.Encoder
import io.circe.syntax._

object DataResponse {
  def complex[T](v: T)(implicit decoder: Encoder[T]): String = {
    s"""{"data": ${v.asJson.spaces4} }"""
  }

  def apply[T](v: T): StandardDataResponse[T] = StandardDataResponse(v)

  def standard[T](v: T): StandardDataResponse[T] = StandardDataResponse(v)
}

case class StandardDataResponse[T](data: T)
