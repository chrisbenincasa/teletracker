package com.teletracker.common.model

import io.circe.generic.JsonCodec
import io.circe.{Encoder, Printer}
import java.util.UUID

object DataResponse {
  def error[T <: BaseErrorResponse[_]: Encoder](
    v: T
  ): DataResponse[ErrorResponse] = {
    DataResponse(v.toResponse)
  }
}

@JsonCodec
case class Paging(
  bookmark: Option[String],
  total: Option[Long] = None)

@JsonCodec
case class DataResponse[T](
  data: T,
  paging: Option[Paging] = None) {
  def withPaging(paging: Paging): DataResponse[T] =
    this.copy(paging = Some(paging))
}

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
