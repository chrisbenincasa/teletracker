package com.teletracker.common.elasticsearch.model

import io.circe.{Codec, Decoder, Encoder}

object StringListOrString {
  implicit final val codec: Codec[StringListOrString] = Codec.from(
    Decoder.decodeString.either(Decoder.decodeArray[String]).map {
      case Left(value) =>
        StringListOrString.forString(value)

      case Right(value) =>
        StringListOrString(value.toList)
    },
    Encoder.encodeString.contramap[StringListOrString](_.get.head)
  )

  def forString(value: String): StringListOrString = new StringListOrString {
    override def get: List[String] = List(value)
  }

  def apply(l: List[String]): StringListOrString = {
    require(l.nonEmpty)
    new StringListOrString {
      override val get: List[String] = l
    }
  }
}

trait StringListOrString extends Equals {
  def get: List[String]

  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[StringListOrString]

  override def equals(obj: Any): Boolean = canEqual(obj) && {
    obj.asInstanceOf[StringListOrString].get.equals(this.get)
  }

  override def toString: String = {
    get.headOption.toString
  }
}
