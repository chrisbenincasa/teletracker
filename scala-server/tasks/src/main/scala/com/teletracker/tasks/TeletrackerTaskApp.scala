package com.teletracker.tasks

import com.google.inject.Module
import com.teletracker.common.inject.{DbProvider, Modules}
import com.teletracker.tasks.inject.HttpClientModule
import com.twitter.app.Flaggable
import java.net.URI
import scala.util.control.NonFatal

abstract class TeletrackerTaskApp[T <: TeletrackerTask: Manifest]
    extends com.twitter.inject.app.App {

  implicit val uriFlaggable: Flaggable[URI] = Flaggable.mandatory(new URI(_))

  implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new HttpClientModule) ++ extraModules

  protected def extraModules: Seq[Module] = Seq()

  protected lazy val dbProvider = injector.instance[DbProvider]

  override protected def run(): Unit = {
    try {
      injector.instance[T].run(collectArgs)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      injector.instance[DbProvider].shutdown()
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
