package com.teletracker.common.inject

import com.codahale.metrics.MetricRegistry
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule

class CodahaleMetricsModule extends TwitterModule {
  @Provides
  @Singleton
  def metricsRegistry: MetricRegistry =
    new com.codahale.metrics.MetricRegistry()
}
