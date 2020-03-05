package com.teletracker.common.util

object EnvironmentDetection {
  def runningRemotely: Boolean =
    Option(System.getenv("AWS_EXECUTION_ENV")).isDefined

  def runningLocally: Boolean = !runningRemotely
}
