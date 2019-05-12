package com.teletracker.service.model

import io.circe.{Encoder, Printer}
import io.circe.syntax._

object DataResponse {
  private val indentedPrinter = Printer.spaces4
  private val compactPriner = Printer.noSpaces.copy(dropNullValues = true)

  def complex[T](v: T, compact: Boolean = false)(implicit encoder: Encoder[T]): String = {
    pure(Map("data" -> v), compact)
  }

  def pure[T](v: T, compact: Boolean = false)(implicit encoder: Encoder[T]): String = {
    val printer = if (compact) compactPriner else indentedPrinter
    printer.pretty(v.asJson)
  }

  def standard[T](v: T): DataResponse[T] = DataResponse(v)
}

case class DataResponse[T](data: T)
