package com.teletracker.common.tasks.model

import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class DenormalizePersonTaskArgs(
  personId: UUID,
  dryRun: Boolean)
