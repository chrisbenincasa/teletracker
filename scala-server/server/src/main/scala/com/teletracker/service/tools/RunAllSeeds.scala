package com.teletracker.service.tools

object RunAllSeedsMain extends TeletrackerJob {
  override protected def runInternal(): Unit = {
    injector.instance[NetworkSeeder].run()
    injector.instance[GenreSeeder].run()
    injector.instance[CertificationSeeder].run()
  }
}
