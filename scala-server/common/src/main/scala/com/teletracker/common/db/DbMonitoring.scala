package com.teletracker.common.db

import com.teletracker.common.monitoring.Monitoring
import com.twitter.util.Stopwatch
import io.opencensus.stats.Measure.MeasureDouble
import io.opencensus.tags.TagKey
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DbMonitoring @Inject()()(implicit monitoring: Monitoring) {
  final private val MethodKey = TagKey.create("method")

  final private val LatencyHistogram = monitoring.createStdHistogram(
    "teletracker/service/db/latency",
    "The latency of DB queries requests to the Teletracker Service",
    MeasureDouble.create("latency", "The latency measured", "ms"),
    Some(List(MethodKey))
  )

  def timed[T](
    method: String
  )(
    f: => Future[T]
  )(implicit executionContext: ExecutionContext
  ): Future[T] = {
    val elapsed = Stopwatch.start()
    val result = f
    result.onComplete(_ => {
      monitoring
        .record(LatencyHistogram, elapsed().inMillis, MethodKey -> method)
    })
    f
  }
}
