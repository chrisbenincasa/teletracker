package com.teletracker.service.util

import com.google.inject.Guice
import com.teletracker.service.inject.ServerModules
import scala.concurrent.ExecutionContext.Implicits.global

class Toolbox {
  val injector = Guice.createInjector(ServerModules(): _*)
}
