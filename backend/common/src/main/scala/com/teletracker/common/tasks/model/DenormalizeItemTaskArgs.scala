package com.teletracker.common.tasks.model

import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class DenormalizeItemTaskArgs(
  itemId: UUID,
  creditsChanged: Boolean,
  crewChanged: Boolean,
  dryRun: Boolean)
