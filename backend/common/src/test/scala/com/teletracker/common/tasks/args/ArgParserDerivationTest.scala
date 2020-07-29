package com.teletracker.common.tasks.args

import com.teletracker.common.tasks.args.ArgParser.Millis
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import org.scalatest.flatspec.AnyFlatSpec
import shapeless.tag.@@
import java.net.URI
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class ArgParserDerivationTest extends AnyFlatSpec with TaskArgImplicits {
  it should "derive" in {
    Map("input" -> "s3://test").parse[DataDumpTaskArgs] match {
      case Failure(exception) =>
        exception.printStackTrace()
        fail("bad", exception)
      case Success(value) =>
        println(value)
        succeed
    }
  }
}

@GenArgParser
case class TestArgs(
  i: Int,
  x: Option[String],
  d: Int = 7)

@GenArgParser
case class IngestJobArgs(
  inputFile: Option[URI],
  crawlerVersion: Option[Long],
  offset: Int = 0,
  limit: Int = -1,
  dryRun: Boolean = true,
  parallelism: Option[Int],
  processBatchSleep: Option[FiniteDuration @@ Millis],
  sleepBetweenWriteMs: Option[Long],
  sourceLimit: Int = -1,
  enableExternalIdMatching: Boolean = true,
  reimport: Boolean = false,
  externalIdFilter: Option[String] = None)

@GenArgParser
@JsonCodec
case class DataDumpTaskArgs(
  input: URI,
  offset: Int = 0,
  limit: Int = -1,
  sleepMs: Int = 250,
  flushEvery: Int = 100,
  rotateEvery: Int = 1000,
  baseOutputPath: Option[String] = None)

object DataDumpTaskArgs
