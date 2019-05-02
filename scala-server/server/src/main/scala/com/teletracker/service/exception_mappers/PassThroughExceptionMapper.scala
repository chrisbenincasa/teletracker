package com.teletracker.service.exception_mappers

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import javax.inject.Inject
import org.slf4j.LoggerFactory

class PassThroughExceptionMapper @Inject()(responseBuilder: ResponseBuilder) extends ExceptionMapper[Exception] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def toResponse(request: Request, throwable: Exception): Response = {
    logger.error("Unexpected error", throwable)
    responseBuilder.status(500)
  }
}
