package com.teletracker.common.util.json.circe

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.joda.time.{DateTime, LocalDate}

trait JodaInstances {
  implicit val jodaDateTimeDecoder: Decoder[DateTime] = Decoder.instance { c =>
    c.focus match {
      case Some(value) if value.isString =>
        Right(new DateTime(value.asString.get))
      case Some(value) if value.isNumber =>
        Right(new DateTime(value.asNumber.map(_.toDouble.toLong).get))
      case Some(x) =>
        Left(DecodingFailure(s"Could not decode value: ${x}", c.history))
      case None => Left(DecodingFailure("???", c.history))
    }
  }

  implicit val jodaDateTimeEncoder: Encoder[DateTime] =
    Encoder.instance[DateTime] { dt =>
      Json.fromString(dt.toString())
    }

  implicit val jodaLocalDateDecoder: Decoder[LocalDate] = Decoder.instance {
    c =>
      c.focus match {
        case Some(value) if value.isString =>
          Right(new LocalDate(value.asString.get))
        case Some(value) if value.isNumber =>
          Right(new LocalDate(value.asNumber.map(_.toDouble.toLong).get))
        case Some(x) =>
          Left(DecodingFailure(s"Could not decode value: ${x}", c.history))
        case None => Left(DecodingFailure("???", c.history))
      }
  }

  implicit val jodaLocalDateEncoder: Encoder[LocalDate] =
    Encoder.instance[LocalDate] { dt =>
      Json.fromString(dt.toString())
    }

  implicit val javaSqlTimestampEncoder: Encoder[java.sql.Timestamp] =
    Encoder.instance { t =>
      Json.fromLong(t.getTime)
    }

  implicit val javaSqlTimestampDecoder: Decoder[java.sql.Timestamp] =
    Decoder.instance { c =>
      c.focus match {
        case Some(value) if value.isNumber =>
          Right(
            new java.sql.Timestamp(value.asNumber.map(_.toDouble.toLong).get)
          )
        case None => Left(DecodingFailure("???", c.history))
      }
    }
}
