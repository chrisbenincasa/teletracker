package com.teletracker.service.testing

import com.teletracker.common.util.{Field, FieldSelector}
import io.circe.parser._
import org.scalatest.flatspec.AnyFlatSpec

class FieldSelectorSpec extends AnyFlatSpec {
  val j =
    """
      |{
      |  "key": {
      |    "value": 1,
      |    "another": false
      |  },
      |  "key2": "value2"
      |}
    """.stripMargin

  val movie =
    """
    |{
    |   "themoviedb": {
    |     "movie": {
    |       "id": 123
    |     }
    |   }
    |}
  """.stripMargin

  val show =
    """
      |{
      |   "themoviedb": {
      |     "show": {
      |       "id": 123,
      |       "random": "123"
      |     }
      |   }
      |}
    """.stripMargin

  "FieldSelector" should "work" in {
    val json = parse(j).right.get

    val res = FieldSelector.filter(json, Field.parse("key").get)

    println(res.spaces4)
  }

  it should "do" in {
    val res = Field.parse("themoviedb{movie{id,poster_path}}")

    println(res)
  }

  it should "what on shows" in {
    val res = FieldSelector.filter(
      parse(show).right.get,
      Field.parse("themoviedb{movie{id,poster_path},show}").get
    )

    println(res)
  }
}
