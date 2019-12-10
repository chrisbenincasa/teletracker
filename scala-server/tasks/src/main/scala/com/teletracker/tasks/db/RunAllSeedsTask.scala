package com.teletracker.tasks.db

import com.teletracker.tasks.{
  DefaultAnyArgs,
  TeletrackerCompoundTask,
  TeletrackerTask
}
import javax.inject.Inject

class RunAllSeedsTask @Inject()(
  networkSeeder: NetworkSeeder,
  genreSeeder: GenreSeeder)
    extends TeletrackerCompoundTask
    with DefaultAnyArgs {

  override def tasks: Seq[TeletrackerTask] =
    Seq(networkSeeder, genreSeeder)
}
