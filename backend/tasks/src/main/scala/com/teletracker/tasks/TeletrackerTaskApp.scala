package com.teletracker.tasks

import com.google.inject.Module
import com.google.inject.util.{Modules => GuiceModules}
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.inject.Modules
import com.teletracker.tasks.inject.{
  FactoriesModule,
  HttpClientModule,
  TaskModules
}
import com.twitter.app.Flaggable
import java.net.URI
import scala.util.control.NonFatal

abstract class TeletrackerTaskApp[T <: TeletrackerTask: Manifest]
    extends com.twitter.inject.app.App {

  implicit val uriFlaggable: Flaggable[URI] = Flaggable.mandatory(new URI(_))

  implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override protected def modules: Seq[Module] =
    Seq(
      GuiceModules
        .`override`(Modules(): _*)
        .`with`(TaskModules() ++ extraModules: _*)
    )

  protected def extraModules: Seq[Module] = Seq()

  override protected def run(): Unit = {
    try {
      injector.instance[T].run(collectArgs)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    }
  }

  protected def collectArgs: Map[String, Option[Any]] = {
    flag
      .getAll()
      .map(f => {
        f.name -> f.getWithDefault
      })
      .toMap
  }

  protected def runInternal(): Unit = {}
}
