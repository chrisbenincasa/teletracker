package com.teletracker.service.tools

import com.google.inject.Module
import com.teletracker.service.inject.{DbProvider, Modules}
import scala.util.control.NonFatal

trait TeletrackerJob extends com.twitter.inject.app.App {
  implicit protected val executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  override protected def modules: Seq[Module] = Modules() ++ extraModules

  protected def extraModules: Seq[Module] = Seq()

  protected lazy val dbProvider = injector.instance[DbProvider]

  final override protected def run(): Unit = {
    try {
      runInternal()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      injector.instance[DbProvider].shutdown()
    }
  }

  protected def runInternal(): Unit = {}
}
