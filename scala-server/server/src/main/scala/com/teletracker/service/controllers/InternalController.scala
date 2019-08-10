package com.teletracker.service.controllers

import com.google.inject.Injector
import com.teletracker.service.auth.AdminFilter
import com.teletracker.service.tools.TeletrackerJob
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class InternalController @Inject()(
  injector: Injector
)(implicit executionContext: ExecutionContext)
    extends Controller {

  private val asyncTaskPool = Executors.newFixedThreadPool(
    10,
    new NamedPoolThreadFactory("teletracker-async-task", true)
  )

  private val jobClazz = classOf[TeletrackerJob]

  filter[AdminFilter] {
    prefix("/api/v1/internal") {
      post("/run-task") { req: RunTaskRequest =>
        val clazz = Class.forName(req.className)
        if (jobClazz.isAssignableFrom(clazz)) {
          val job = injector.getInstance(clazz).asInstanceOf[TeletrackerJob]
          val args = req.args.map(_.mapValues(Option(_))).getOrElse(Map.empty)

          job.preparseArgs(args)

          asyncTaskPool.submit(new Runnable {
            override def run(): Unit = job.run(args)
          })
        } else {
          response.badRequest("Not a teletracker job")
        }
      }
    }
  }
}

case class RunTaskRequest(
  className: String,
  args: Option[Map[String, Any]])
