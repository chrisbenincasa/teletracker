package com.teletracker.service.testing

import org.scalatest.FlatSpec

class ArgParserTest extends FlatSpec {
  it should "parse" in {
    import com.teletracker.service.util.Args._

    val args = Map[String, Option[Any]](
      "string" -> None,
      "string2" -> Some(1)
    )

    val xyz = args.valueOrDefault("string2", 2)

    println(args.value[Int]("string2"))
  }
}
