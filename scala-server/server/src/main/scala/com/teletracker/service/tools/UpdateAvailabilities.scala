package com.teletracker.service.tools

import com.teletracker.service.db.access.ThingsDbAccess
import com.teletracker.service.util.Futures._

object UpdateAvailabilities extends TeletrackerJob {
  override protected def runInternal(): Unit = {
    val access = injector.instance[ThingsDbAccess]

    access
      .findExpiredAvailabilities()
      .map(_.flatMap(_.id).toSet)
      .flatMap(access.markAvailabilities(_, isAvailable = false))
      .await()
  }
}
