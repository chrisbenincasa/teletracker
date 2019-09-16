package com.teletracker.common.inject

import com.codahale.metrics.MetricRegistry
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule
import io.opencensus.contrib.dropwizard.DropWizardMetrics
import io.opencensus.stats.{Stats, StatsRecorder, ViewManager}
import io.opencensus.tags.{Tagger, Tags}
import scala.collection.JavaConverters._

class OpenCensusMetricsModule extends TwitterModule {
  @Provides
  @Singleton
  def viewManager: ViewManager = Stats.getViewManager

  @Provides
  @Singleton
  def tagger: Tagger = Tags.getTagger

  @Provides
  @Singleton
  def statsRecorder(metricRegistry: MetricRegistry): StatsRecorder = {
    io.opencensus.metrics.Metrics.getExportComponent.getMetricProducerManager
      .add(new DropWizardMetrics(List(metricRegistry).asJava))

    Stats.getStatsRecorder
  }
}
