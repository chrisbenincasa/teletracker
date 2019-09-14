package com.teletracker.common.util

import scala.util.control.NoStackTrace

object RequireLite {
  @inline def requireLite(
    requirement: Boolean,
    message: => Any
  ): Unit = {
    if (!requirement) {
      throw new IllegalArgumentException(s"requirement failed: $message")
      with NoStackTrace
    }
  }
}
