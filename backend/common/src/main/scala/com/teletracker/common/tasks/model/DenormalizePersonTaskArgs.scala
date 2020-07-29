package com.teletracker.common.tasks.model

import com.teletracker.common.tasks.args.GenArgParser
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
@GenArgParser
case class DenormalizePersonTaskArgs(
  personId: UUID,
  dryRun: Boolean = true)

object DenormalizePersonTaskArgs
