package com.teletracker.tasks.db

import com.teletracker.tasks.{TeletrackerCompoundTask, TeletrackerTask}
import javax.inject.Inject

class RunAllSeedsTask @Inject()(
  networkSeeder: NetworkSeeder,
  genreSeeder: GenreSeeder,
  certificationSeeder: CertificationSeeder)
    extends TeletrackerCompoundTask {

  override def tasks: Seq[TeletrackerTask] =
    Seq(networkSeeder, genreSeeder, certificationSeeder)
}
