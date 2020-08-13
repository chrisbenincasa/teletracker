package com.teletracker.tasks.scraper

import com.teletracker.common.tasks.args.ArgParser.Millis
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import shapeless.tag
import shapeless.tag.@@
import java.net.URI
import scala.concurrent.duration._

@GenArgParser
@JsonCodec
case class IngestJobArgs(
  inputFile: Option[URI],
  crawlerVersion: Option[Long],
  override val offset: Int = 0,
  override val limit: Int = -1,
  override val dryRun: Boolean = true,
  override val parallelism: Option[Int],
  override val processBatchSleep: Option[FiniteDuration @@ Millis] =
    Some(tag[Millis](500 millis)),
  override val sleepBetweenWriteMs: Option[Long],
  sourceLimit: Int = -1,
  enableExternalIdMatching: Boolean = true,
  reimport: Boolean = false,
  externalIdFilter: Option[String],
  updateAsync: Boolean = true)
    extends IngestJobArgsLike

object IngestJobArgs
