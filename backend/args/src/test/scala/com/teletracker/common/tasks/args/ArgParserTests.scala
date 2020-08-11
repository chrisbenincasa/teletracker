package com.teletracker.common.tasks.args

import org.scalatest.Assertions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Failure, Success}

class ArgParserTests extends AnyFlatSpec with Matchers {
  "ArgParser#intArg" should "work with doubles" in {
    val x = 1.0d

    ArgParser.intArg.parse(ArgValue(x)) match {
      case Failure(exception) => fail(exception)
      case Success(value)     => value shouldEqual 1
    }
  }

  "ArgParser#floatArg" should "work with doubles" in {
    val x = 1.0d

    ArgParser.floatArg.parse(ArgValue(x)) match {
      case Failure(exception) => fail(exception)
      case Success(value)     => value shouldEqual 1.0f
    }
  }
}
