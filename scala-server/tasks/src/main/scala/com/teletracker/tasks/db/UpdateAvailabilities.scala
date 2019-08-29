package com.teletracker.tasks.db

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskApp

object UpdateAvailabilities extends TeletrackerTaskApp {
  override protected def runInternal(): Unit = {
    val access = injector.instance[ThingsDbAccess]

    access
      .findExpiredAvailabilities()
      .map(_.flatMap(_.id).toSet)
      .flatMap(access.markAvailabilities(_, isAvailable = false))
      .await()
  }
}
