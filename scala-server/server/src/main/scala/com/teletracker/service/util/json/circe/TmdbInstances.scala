package com.teletracker.service.util.json.circe

import com.teletracker.service.model.tmdb.{Movie, Person, PersonCredits, TvShow}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder}
import shapeless.{:+:, CNil, Inl, Inr}

trait TmdbInstances {
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

  implicit val personCreditsDecoder: Decoder[PersonCredits] = deriveDecoder

  implicit val personCreditsEncoder: Encoder[PersonCredits] = deriveEncoder

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

  implicit def decodeSearchResult(
    implicit
    m: Decoder[Movie],
    t: Decoder[TvShow]
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
