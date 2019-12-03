package com.teletracker.common.monitoring

import com.twitter.util.Stopwatch
import org.slf4j.Logger
import com.twitter.util.logging.{Logger => TLogger}
import scala.concurrent.{ExecutionContext, Future}

object Timing {
  def time[T](
    method: String,
    logger: Logger
  )(
    f: => Future[T]
  )(implicit executionContext: ExecutionContext
  ): Future[T] = {
    val elapsed = Stopwatch.start()
    val result = f
    result.onComplete(_ => {
      logger.info(s"$method took ${elapsed().inMillis} millis to complete")
    })
    f
  }

  def time[T](
    method: String
  )(
    f: => Future[T]
  )(implicit executionContext: ExecutionContext
  ): Future[T] = {
    val elapsed = Stopwatch.start()
    val result = f
    result.onComplete(_ => {
      println(s"$method took ${elapsed().inMillis} millis to complete")
    })
    f
  }
}
