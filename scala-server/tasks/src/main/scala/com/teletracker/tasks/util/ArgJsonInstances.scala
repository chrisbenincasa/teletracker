package com.teletracker.tasks.util

import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestDeltaJobArgs
import com.teletracker.tasks.tmdb.import_tasks.ImportTmdbDumpTaskArgs
import io.circe.Encoder

object ArgJsonInstances {
  implicit val importTmdbDumpTaskArgsEncoder
    : Encoder.AsObject[ImportTmdbDumpTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder[ImportTmdbDumpTaskArgs]

  implicit val ingestDeltaJobTaskArgsEncoder
    : Encoder.AsObject[IngestDeltaJobArgs] =
    io.circe.generic.semiauto.deriveEncoder[IngestDeltaJobArgs]
}
