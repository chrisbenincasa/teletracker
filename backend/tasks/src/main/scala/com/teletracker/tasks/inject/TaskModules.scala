package com.teletracker.tasks.inject

import com.google.inject.Module
import com.teletracker.common.inject.QueuesModule
import com.teletracker.tasks.config.TasksConfigModule
import scala.concurrent.ExecutionContext

object TaskModules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] =
    Seq(
      new HttpClientModule,
      new FactoriesModule,
      new QueuesModule,
      new TasksConfigModule
    )
}
