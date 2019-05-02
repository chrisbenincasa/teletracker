package com.teletracker.service.tools

import com.teletracker.service.inject.Modules
import com.google.inject.Module
import com.twitter.inject.app.App
import scala.concurrent.ExecutionContext.Implicits.global

object RunAllSeedsMain extends RunAllSeeds

class RunAllSeeds extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    injector.instance[NetworkSeeder].run()
    injector.instance[GenreSeeder].run()
    injector.instance[CertificationSeeder].run()
  }
}
