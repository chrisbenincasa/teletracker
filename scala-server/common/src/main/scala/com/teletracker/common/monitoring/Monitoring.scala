package com.teletracker.common.monitoring

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.service.Backoff
import io.opencensus.common.Scope
import io.opencensus.stats.Aggregation.{Count, Distribution}
import io.opencensus.stats.Measure.{MeasureDouble, MeasureLong}
import io.opencensus.stats.View.Name
import io.opencensus.stats._
import io.opencensus.tags.{TagContextBuilder, TagKey, TagValue, Tags}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.math.Numeric
import scala.util.Try

object Monitoring {
  private val viewManager = Stats.getViewManager
  private val tagger = Tags.getTagger
  private val recorder: StatsRecorder = Stats.getStatsRecorder

  final val DefaultHistoAggregation =
    Distribution.create(
      BucketBoundaries.create(
        (List(0.0) ++ Backoff
          .exponential(50.millis, 2)
          .take(10)
          .toList
          .map(_.inMillis.toDouble)).map(new java.lang.Double(_)).asJava
      )
    )

  final private val createCounters =
    new ConcurrentHashMap[(String, Option[List[TagKey]]), Measure]

  final private val createdHistos =
    new ConcurrentHashMap[(String, Option[List[TagKey]]), Measure]
}

class Monitoring {
  import Monitoring._

  def createStdCounter(
    name: String,
    description: String,
    measure: MeasureLong,
    columns: Option[List[TagKey]]
  ): MeasureLong = {
    createCounters
      .computeIfAbsent((name, columns), _ => {
        register(name, description, measure, Count.create(), columns)
        measure
      })
      .asInstanceOf[MeasureLong]
  }

  def createStdHistogram(
    name: String,
    description: String,
    measure: MeasureDouble,
    columns: Option[List[TagKey]],
    agg: Aggregation = DefaultHistoAggregation
  ): MeasureDouble = {
    createdHistos
      .computeIfAbsent((name, columns), _ => {
        register(name, description, measure, agg, columns)
        measure
      })
      .asInstanceOf[MeasureDouble]
  }

  def register[T <: Measure](
    name: String,
    description: String,
    measure: T,
    agg: Aggregation,
    columns: Option[List[TagKey]]
  ): T = {
    val view = View.create(
      Name.create(name),
      description,
      measure,
      agg,
      columns.map(_.asJava).getOrElse(new java.util.ArrayList)
    )

    register(view)

    measure
  }

  def register(view: View): Unit = {
    viewManager.registerView(view)
  }

  def record[T](
    measureDouble: MeasureDouble,
    value: T,
    tags: (TagKey, String)*
  )(implicit f: Numeric[T]
  ): Try[Unit] = {
    withTagContext(tags) { _ =>
      recorder.newMeasureMap().put(measureDouble, f.toDouble(value)).record()
    }
  }

  def incr(
    measureLong: MeasureLong,
    tags: (TagKey, String)*
  ): Try[Unit] = {
    record(measureLong, 1L, tags: _*)
  }

  def record[T](
    measureLong: MeasureLong,
    value: T,
    tags: (TagKey, String)*
  )(implicit f: Numeric[T]
  ): Try[Unit] = {
    withTagContext(tags) { _ =>
      recorder.newMeasureMap().put(measureLong, f.toLong(value)).record()
    }
  }

  private def buildTagMap(kvs: Seq[(TagKey, String)]): TagContextBuilder = {
    val builder = tagger.emptyBuilder()
    kvs
      .map {
        case (key, value) => key -> TagValue.create(value)
      }
      .foreach(Function.tupled(builder.putLocal))
    builder
  }

  private def withTagContext[T](
    kvs: Seq[(TagKey, String)]
  )(
    f: Scope => T
  ): Try[T] = {
    withTry(buildTagMap(kvs).buildScoped())(f)
  }

  private def withTry[T, U](t: => T)(f: T => U): Try[U] = {
    Try(t).map(f)
  }
}
