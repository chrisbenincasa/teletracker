package com.teletracker.service.monitoring

import com.teletracker.common.monitoring.Monitoring
import com.twitter.finagle.stats.{
  Counter,
  Gauge,
  Stat,
  StatsReceiver,
  Verbosity
}
import io.opencensus.stats.Measure
import io.opencensus.stats.Measure.MeasureLong
import java.util.concurrent.ConcurrentHashMap

class OpenCensusStatsReceiver extends StatsReceiver {
  final private val counterCache = new ConcurrentHashMap[Seq[String], Counter]()
  final private val monitor = new Monitoring

  override def repr: AnyRef = this

  override def counter(
    verbosity: Verbosity,
    name: String*
  ): Counter = {
    counterCache.computeIfAbsent(name, _ => new OpenCensusCounter(name))
  }

  override def stat(
    verbosity: Verbosity,
    name: String*
  ): Stat = {
    new Stat {
      override def add(value: Float): Unit =
        if (!name.startsWith(Seq("finagle"))) {
          println(s"stat: ${name} = $value")
        }
    }
  }

  override def addGauge(
    verbosity: Verbosity,
    name: String*
  )(
    f: => Float
  ): Gauge = new Gauge {
    override def remove(): Unit = {
      println("remove")
    }
  }

  private class OpenCensusCounter(key: Seq[String]) extends Counter {
    final private val measure = monitor.createStdCounter(
      key.mkString("/"),
      key.mkString("_"),
      MeasureLong.create(createMeasureName(key), "???", "???"),
      None
    )

    override def incr(delta: Long): Unit = {
      monitor.record(measure, delta)
    }
  }

  private def createMeasureName(key: Seq[String]) = {
    require(key.nonEmpty)
    val i = key.indexWhere(_.startsWith("per_"))
    if (i <= 0) {
      key.last
    } else {
      key(i - 1)
    }
  }
}
