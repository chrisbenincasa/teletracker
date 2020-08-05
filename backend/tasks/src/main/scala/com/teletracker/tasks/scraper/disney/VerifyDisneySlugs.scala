package com.teletracker.common.model.scraping.disney

import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import io.circe.generic.JsonCodec
import java.net.URI

@JsonCodec
@GenArgParser
case class VerifyDisneySlugsArgs(input: URI)

class VerifyDisneySlugs extends TypedTeletrackerTask[VerifyDisneySlugsArgs] {}
