package com.teletracker.common.config.core.api

import java.util.concurrent.ScheduledFuture

/**
  * Wrapper to cancel scheduled futures
  *
  * @param scheduledFuture
  */
class WatchToken(scheduledFuture: Option[ScheduledFuture[_]]) {
  def cancel(): Unit = scheduledFuture.map(_.cancel(true))
}
