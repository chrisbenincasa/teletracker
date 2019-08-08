package com.teletracker.service.tools

import com.google.inject.Module
import com.teletracker.common.inject.{DbProvider, Modules}
import scala.util.control.NonFatal

abstract class TeletrackerJobApp[T <: TeletrackerJob: Manifest]
    extends com.twitter.inject.app.App {
  implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override protected def modules: Seq[Module] = Modules() ++ extraModules

  protected def extraModules: Seq[Module] = Seq()

  protected lazy val dbProvider = injector.instance[DbProvider]

  final override protected def run(): Unit = {
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

trait TeletrackerJob {
  type Args = Map[String, Option[Any]]

  def preparseArgs(args: Args): Unit = {}
  def run(): Unit = run(Map.empty)
  def run(args: Args): Unit
}
