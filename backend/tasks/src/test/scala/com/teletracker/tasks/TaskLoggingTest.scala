package com.teletracker.tasks

import com.google.inject.Guice
import com.teletracker.common.inject.Modules
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec

class TaskLoggingTest extends AnyFlatSpec {
  it should "idk" in {
    val injector = Guice.createInjector(Modules(): _*)

    val runner = new TeletrackerTaskRunner(injector)
    (0 to 3).foreach(_ => {
      runner.runFromString(
        "com.teletracker.tasks.general.LoggingTask",
        Map("s3Logging" -> Some(true))
      )
    })
  }
}
