package com.chrisbenincasa.services.teletracker.tools

import com.chrisbenincasa.services.teletracker.inject.Modules
import com.google.inject.Module
import com.twitter.inject.app.App
import scala.concurrent.ExecutionContext.Implicits.global

object RunAllSeeds extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    injector.instance[NetworkSeeder].run()
    injector.instance[GenreSeeder].run()
    injector.instance[CertificationSeeder].run()
  }
}
