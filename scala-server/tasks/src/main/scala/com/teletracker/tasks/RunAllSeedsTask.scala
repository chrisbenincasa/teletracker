package com.teletracker.tasks

import javax.inject.Inject

class RunAllSeedsTask @Inject()(
  networkSeeder: NetworkSeeder,
  genreSeeder: GenreSeeder,
  certificationSeeder: CertificationSeeder)
    extends TeletrackerCompoundTask {

  override def tasks: Seq[TeletrackerTask] =
    Seq(networkSeeder, genreSeeder, certificationSeeder)
}
