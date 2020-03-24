//package com.teletracker.service.filters
//
//import com.teletracker.common.monitoring.Monitoring
//import com.twitter.finagle.{Service, SimpleFilter}
//import com.twitter.finagle.http.{Request, Response, Status}
//import com.twitter.finatra.http.contexts.RouteInfo
//import com.twitter.util.{Duration, Future, Return, Stopwatch, Throw}
//import io.opencensus.stats.Measure.{MeasureDouble, MeasureLong}
//import io.opencensus.tags.TagKey
//import javax.inject.Inject
//
//class OpenCensusMonitoringFilter @Inject()()(implicit monitoring: Monitoring)
//    extends SimpleFilter[Request, Response] {
//
//  final private val CodeKey = TagKey.create("code")
//  final private val RouteKey = TagKey.create("route")
//  final private val MethodKey = TagKey.create("method")
//
//  final private val StatusCounter = monitoring.createStdCounter(
//    "teletracker/service/http/status",
//    "The number of time an HTTP response code was served by the Teletracker Service",
//    MeasureLong.create("status", "The status code returned", "count"),
//    Some(List(CodeKey, RouteKey, MethodKey))
//  )
//
//  final private val StatusClassCounter = monitoring.createStdCounter(
//    "teletracker/service/http/status_class",
//    "The number of time an HTTP response code was served by the Teletracker Service",
//    MeasureLong.create("status_class", "x", "count"),
//    Some(List(CodeKey, RouteKey, MethodKey))
//  )
//
//  final private val LatencyHistogram = monitoring.createStdHistogram(
//    "teletracker/service/http/latency",
//    "The latency of HTTP requests to the Teletracker Service",
//    MeasureDouble.create("latency", "The latency measured", "ms"),
//    Some(List(CodeKey, RouteKey, MethodKey))
//  )
//
//  override def apply(
//    request: Request,
//    service: Service[Request, Response]
//  ): Future[Response] = {
//    val elapsed = Stopwatch.start()
//    val future = service(request)
//    future respond {
//      case Return(response) =>
//        count(elapsed(), request, response)
//      case Throw(_) =>
//        // Treat exceptions as empty 500 errors
//        val response = Response(request.version, Status.InternalServerError)
//        count(elapsed(), request, response)
//    }
//  }
//
//  protected def count(
//    duration: Duration,
//    request: Request,
//    response: Response
//  ): Unit = {
//    val statusCode = response.statusCode.toString
//    val statusClass = (response.statusCode / 100).toString + "XX"
//
//    val extraKeys = RouteInfo(request) match {
//      case Some(info) =>
//        Seq(
//          RouteKey -> info.sanitizedPath,
//          MethodKey -> request.method.toString()
//        )
//      case None => Seq()
//    }
//
//    val tags = Seq(CodeKey -> statusCode) ++ extraKeys
//
//    monitoring.incr(StatusCounter, tags: _*)
//    monitoring.incr(
//      StatusClassCounter,
//      tags: _*
//    )
//
//    monitoring.record(LatencyHistogram, duration.inMillis, tags: _*)
//  }
//}
