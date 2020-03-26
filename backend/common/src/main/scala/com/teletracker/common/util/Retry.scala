package com.teletracker.common.util

import com.teletracker.common.util.Retry.RetryOptions
import com.twitter.finagle.service.Backoff
import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import com.teletracker.common.util.time.DurationConverters._
import org.slf4j.LoggerFactory

object Retry {
  case class RetryOptions(
    maxAttempts: Int,
    initialDuration: FiniteDuration,
    maxDuration: FiniteDuration,
    shouldRetry: PartialFunction[Throwable, Boolean] = {
      case NonFatal(_) =>
        true
    })
}

class Retry(
  scheduler: ScheduledExecutorService
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  def withRetries[T](
    options: RetryOptions
  )(
    action: () => Future[T]
  ): Future[T] = {
    val initialBackoffs = Backoff
      .exponentialJittered(
        options.initialDuration.asTwitter,
        options.maxDuration.asTwitter
      )
      .take(options.maxAttempts)
      .map(_.asScala)

    def doIt(
      attempt: Int,
      backoffs: Stream[FiniteDuration]
    ): Future[T] = {
      action().transformWith {
        case Success(value) => Future.successful(value)
        case Failure(exception)
            if options.shouldRetry.isDefinedAt(exception) && options.shouldRetry
              .apply(exception) =>
          backoffs match {
            case Stream.Empty =>
              logger.warn("Reached maximum retry attempts. Failing")
              Future.failed(exception)
            case head #:: tail =>
              logger.info(
                s"Attempt $attempt failed. Retrying after ${head.toSeconds} seconds."
              )
              val p = Promise[T]()
              scheduler.schedule(new Runnable {
                override def run(): Unit = {
                  p.completeWith(doIt(attempt + 1, tail))
                }
              }, head.length, head.unit)
              p.future
          }
        case Failure(ex) => Future.failed(ex)
      }
    }

    doIt(0, initialBackoffs)
  }
}
