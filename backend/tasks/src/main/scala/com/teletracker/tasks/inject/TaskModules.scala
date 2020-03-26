package com.teletracker.tasks.inject

import com.google.inject.Module
import scala.concurrent.ExecutionContext

object TaskModules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] =
    Seq(
      new HttpClientModule,
      new FactoriesModule
    )

}
