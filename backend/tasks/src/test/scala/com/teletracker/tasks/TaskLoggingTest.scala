package com.teletracker.tasks

import com.google.inject.Guice
import com.teletracker.common.inject.Modules
import org.scalatest.FlatSpec
import scala.concurrent.ExecutionContext.Implicits.global

class TaskLoggingTest extends FlatSpec {
  it should "idk" in {
    val injector = Guice.createInjector(Modules(): _*)

    val runner = new TeletrackerTaskRunner(injector)
    (0 to 3).foreach(_ => {
      runner.run(
        "com.teletracker.tasks.general.LoggingTask",
        Map("s3Logging" -> Some(true))
      )
    })
  }
}
