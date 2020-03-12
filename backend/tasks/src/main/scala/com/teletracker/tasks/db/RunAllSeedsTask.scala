package com.teletracker.tasks.db

import com.teletracker.common.tasks.{DefaultAnyArgs, TeletrackerTask}
import com.teletracker.tasks.TeletrackerCompoundTask
import javax.inject.Inject

class RunAllSeedsTask @Inject()(
  networkSeeder: NetworkSeeder,
  genreSeeder: GenreSeeder)
    extends TeletrackerCompoundTask
    with DefaultAnyArgs {

  override def tasks: Seq[TeletrackerTask] =
    Seq(networkSeeder, genreSeeder)
}
