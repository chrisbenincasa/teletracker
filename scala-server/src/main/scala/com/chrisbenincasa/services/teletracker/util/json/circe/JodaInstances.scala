package com.chrisbenincasa.services.teletracker.util.json.circe

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.joda.time.DateTime

trait JodaInstances {
  implicit val jodaDateTimeDecoder: Decoder[DateTime] = Decoder.instance{ c =>
    c.focus match {
      case Some(value) if value.isString => Right(new DateTime(value.asString.get))
      case Some(value) if value.isNumber => Right(new DateTime(value.asNumber.map(_.toDouble.toLong)))
      case Some(x) => Left(DecodingFailure(s"Could not decode value: ${x}", c.history))
      case None => Left(DecodingFailure("???", c.history))
    }
  }

  implicit val jodaDateTimeEncoder: Encoder[DateTime] = Encoder.instance[DateTime] { dt =>
    Json.fromString(dt.toString())
  }
}
