package com.teletracker.tasks

import cats.Monoid
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import org.scalatest.FlatSpec

object MapMonoidTest {
  implicit val config: Configuration = Configuration.default.copy()
}

class MapMonoidTest extends FlatSpec {
  import cats.implicits._

  it should "combine" in {
    val left = Map("a" -> Nil, "b" -> List(1))
    val right = Map("a" -> List(2), "b" -> List(2), "c" -> List(3, 4))

    val comb = left |+| right

    println(left |+| right)
  }

  import io.circe.syntax._
  it should "do" in {
    println(Test(Some(List())).asJson.dropNullValues.spaces2)
  }
}

@JsonCodec
case class Test(x: Option[List[Int]])
