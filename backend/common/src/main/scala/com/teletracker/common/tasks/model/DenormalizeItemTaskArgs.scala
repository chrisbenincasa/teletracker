package com.teletracker.common.tasks.model

import com.teletracker.common.tasks.args.GenArgParser
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
@GenArgParser
case class DenormalizeItemTaskArgs(
  itemId: UUID,
  creditsChanged: Boolean,
  crewChanged: Boolean,
  dryRun: Boolean = true)
