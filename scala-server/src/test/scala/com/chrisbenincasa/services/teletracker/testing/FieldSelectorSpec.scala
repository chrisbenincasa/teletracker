package com.chrisbenincasa.services.teletracker.testing

import com.chrisbenincasa.services.teletracker.util.{Field, FieldSelector}
import org.scalatest.FlatSpec
import io.circe.parser._

class FieldSelectorSpec extends FlatSpec {
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

  "FieldSelector" should "work" in {
    val json = parse(j).right.get

    val res = FieldSelector.filter(json, Field.parse("key").get)

    println(res.spaces4)
  }

  it should "do" in {
    val res = Field.parse("themoviedb{movie{id,poster_path}}")

    println(res)
  }
}
