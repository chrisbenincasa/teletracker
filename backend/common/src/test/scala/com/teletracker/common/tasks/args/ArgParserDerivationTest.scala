package com.teletracker.common.tasks.args

import org.scalatest.flatspec.AnyFlatSpec
import scala.util.{Failure, Success}

class ArgParserDerivationTest extends AnyFlatSpec with TaskArgImplicits {
  it should "derive" in {
    Map("i" -> 1, "x" -> "str").parse[TestArgs] match {
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
