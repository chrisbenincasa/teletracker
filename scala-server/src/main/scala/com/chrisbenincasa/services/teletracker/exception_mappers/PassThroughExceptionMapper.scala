package com.chrisbenincasa.services.teletracker.exception_mappers

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.Inject

class PassThroughExceptionMapper @Inject()(responseBuilder: ResponseBuilder) extends ExceptionMapper[Exception] {
  override def toResponse(request: Request, throwable: Exception): Response = {
    throwable.printStackTrace()
    responseBuilder.status(500)
  }
}
