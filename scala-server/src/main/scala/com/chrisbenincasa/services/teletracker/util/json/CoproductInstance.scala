package com.chrisbenincasa.services.teletracker.util.json

import com.chrisbenincasa.services.teletracker.model.tmdb._
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.joda.time.DateTime
import shapeless._

trait LowPriDecoders {
  implicit def decodeSearchResult(
    implicit
    m: Decoder[Movie],
    t: Decoder[TvShow],
    p: Decoder[Person]
  ): Decoder[Movie :+: TvShow :+: Person :+: CNil] = Decoder.instance { c =>
    c.focus match {
      case Some(value) =>
        value.hcursor.downField("media_type").focus match {
          case Some(v) if v.isString =>
            if (v.asString.get == "movie") {
              value.as[Movie].map(Inl(_))
            } else if (v.asString.get == "tv") {
              value.as[TvShow].map(x => Inr(Inl(x)))
            } else if (v.asString.get == "person") {
              value.as[Person].map(x => Inr(Inr(Inl(x))))
            } else {
              Left(DecodingFailure("???", c.history))
            }
        }
      case None => Left(DecodingFailure("???", c.history))
    }
  }
}

object SearchResultDecoders extends LowPriDecoders  {
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

  implicit val decodeCNil: Decoder[CNil] =
    Decoder.instance(c => Left(DecodingFailure("CNil", c.history)))

  implicit def decodeMovieOrTvResult(
    implicit
    m: Decoder[Movie],
    t: Decoder[TvShow]
  ): Decoder[Movie :+: TvShow :+: CNil] = Decoder.instance { c =>
    c.focus match {
      case Some(value) =>
        value.hcursor.downField("media_type").focus match {
          case Some(v) if v.isString =>
            if (v.asString.get == "movie") {
              value.as[Movie].map(Inl(_))
            } else if (v.asString.get == "tv") {
              value.as[TvShow].map(x => Inr(Inl(x)))
            } else {
              Left(DecodingFailure("???", c.history))
            }
        }
      case None => Left(DecodingFailure("???", c.history))
    }
  }

  import io.circe.shapes._

  implicit def personDecoder(
    implicit
    m: Decoder[Movie],
    t: Decoder[TvShow]
  ): Decoder[Person] = deriveDecoder[Person]

  implicit def personEncoder(
    implicit
    m: Decoder[Movie],
    t: Decoder[TvShow]
  ): Encoder[Person] = deriveEncoder[Person]
}
