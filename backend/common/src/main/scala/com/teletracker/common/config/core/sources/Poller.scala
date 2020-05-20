package com.teletracker.common.config.core.sources

import scala.concurrent.duration.{Duration, _}

/**
  * Only for testing purposes
  */
private[config] object Poller {
  def poll[T](
    max: Duration,
    rate: Duration = 100 milli
  )(
    block: => Boolean
  ): Unit = {
    var maxMutable = max.toMillis

    var result = false

    // poll on changes
    while (maxMutable >= 0) {
      try {
        result = block

        if (!result) {
          throw new RuntimeException
        }

        maxMutable = -1
      } catch {
        case _: Throwable =>
          maxMutable = maxMutable - rate.toMillis

          Thread.sleep(rate.toMillis)
      }
    }
  }
}
