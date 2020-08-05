package com.teletracker.tasks.util

import com.teletracker.common.tasks.args
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ArgParserTest
    extends AnyFlatSpec
    with args.TaskArgImplicits
    with Matchers {
  "ArgParser" should "parse ints" in {
    val m: Map[String, Option[Any]] = Map(
      "x" -> Some(1),
      "y" -> Some("1")
    )

    val maybeInt = m.value[Int]("x")
    val maybeInt2 = m.value[Int]("y")

    maybeInt shouldEqual Some(1)
    maybeInt2 shouldEqual Some(1)
  }
}
