package com.teletracker.service.tools

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.util.Futures._

object UpdateAvailabilities extends TeletrackerJobApp {
  override protected def runInternal(): Unit = {
    val access = injector.instance[ThingsDbAccess]

    access
      .findExpiredAvailabilities()
      .map(_.flatMap(_.id).toSet)
      .flatMap(access.markAvailabilities(_, isAvailable = false))
      .await()
  }
}
