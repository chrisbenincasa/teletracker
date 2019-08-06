package com.teletracker.common.model

import io.circe.{Encoder, Printer}
import io.circe.syntax._
import io.circe.generic.auto._
import java.util.UUID

object DataResponse {
  private val indentedPrinter = Printer.spaces4.copy(dropNullValues = true)
  private val compactPrinter = Printer.noSpaces.copy(dropNullValues = true)

  def complex[T](
    v: T,
    compact: Boolean = false
  )(implicit encoder: Encoder[T]
  ): String = {
    pure(DataResponse(v))
  }

  def error[T <: BaseErrorResponse[_]](
    v: T,
    compact: Boolean = false
  ): String = {
    pure(v.toResponse)
  }

  def pure[T](
    v: T,
    compact: Boolean = false
  )(implicit encoder: Encoder[T]
  ): String = {
    val printer = if (compact) compactPrinter else indentedPrinter
    printer.pretty(v.asJson)
  }

  def standard[T](v: T): DataResponse[T] = DataResponse(v)
}

case class DataResponse[T](data: T)

case class ErrorResponse(error: StandardErrorDetails)
case class StandardErrorDetails(
  message: String,
  `@type`: String,
  id: String)

abstract class BaseErrorResponse[T <: Throwable](
  val error: T,
  val id: String = UUID.randomUUID().toString) {
  def toResponse: ErrorResponse =
    ErrorResponse(
      StandardErrorDetails(error.getMessage, error.getClass.getSimpleName, id)
    )
}

class IllegalActionTypeError(actionType: String)
    extends BaseErrorResponse(
      new IllegalArgumentException(
        s"Unrecognized action type for string $actionType"
      )
    )
