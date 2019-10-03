package com.teletracker.tasks.util

import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.tmdb.import_tasks.ImportTmdbDumpTaskArgs
import io.circe.Encoder

object ArgJsonInstances {
  implicit val importTmdbDumpTaskArgsEncoder
    : Encoder.AsObject[ImportTmdbDumpTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder[ImportTmdbDumpTaskArgs]
}
