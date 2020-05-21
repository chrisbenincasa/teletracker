package com.teletracker.common.model.wikidata

import com.teletracker.common.util.time.OffsetDateTimeUtils
import io.circe.generic.JsonCodec
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField.{DAY_OF_MONTH, MONTH_OF_YEAR, YEAR}
import scala.util.{Failure, Success, Try}

/*
 {
  "id": "Q60",
  "type": "item",
  "labels": {},
  "descriptions": {},
  "aliases": {},
  "claims": {},
  "sitelinks": {},
  "lastrevid": 195301613,
  "modified": "2015-02-10T12:42:02Z"
}
 */
@JsonCodec
case class Entity(
  id: String,
  `type`: String,
  labels: Map[String, Label],
  descriptions: Map[String, Description],
  aliases: Map[String, List[Alias]],
  claims: Map[String, List[Claim]]) {

  def imdbId: Option[String] = claims.get(WikibaseProperties.ImdbId) match {
    case Some(value) =>
      value.headOption.flatMap(_.mainsnak.datavalue).collectFirst {
        case StringDataValue(value, _) => value
      }
    case None => None
  }
}

@JsonCodec
case class Label(
  language: String,
  value: String)

@JsonCodec
case class Description(
  language: String,
  value: String)

@JsonCodec
case class Alias(
  language: String,
  value: String)

@JsonCodec
case class Claim(
  id: String,
  `type`: String,
  rank: String,
  mainsnak: Snak,
  qualifiers: Option[Map[String, List[Snak]]],
  references: Option[List[Reference]]) {

  def referencesPropertyWithId(
    property: String,
    id: String
  ): Boolean = {
    references.exists(
      _.exists(
        _.hasWikibaseIdReference(
          property,
          id
        )
      )
    )
  }
}

@JsonCodec
case class Snak(
  snaktype: String,
  property: String,
  datatype: String,
  datavalue: Option[DataValue])

object DataValue {
  import io.circe.syntax._

  implicit final val datavalueDecoder: Decoder[DataValue] =
    Decoder.instance[DataValue] { cursor =>
      cursor.downField("type").focus match {
        case Some(value) if value.isString =>
          value.asString.get match {
            case "string" =>
              cursor.value
                .as[StringDataValue]
                .left
                .map(_.withMessage("Could not decode StringDataValue"))
            case "monolingualtext" =>
              cursor
                .downField("value")
                .as[MonolingualTextDataValue]
                .left
                .map(_.withMessage("Could not decode StringDataValue"))
            case "wikibase-entityid" =>
              cursor.value
                .as[WikibaseEntityIdDataValue]
                .left
                .map(
                  _.withMessage("Could not decode WikibaseEntityIdDataValue")
                )
            case "globecoordinate" =>
              cursor
                .downField("value")
                .as[GlobeCoordinateDataValue]
                .left
                .map(_.withMessage("Could not decode GlobeCoordinateDataValue"))
            case "quantity" =>
              cursor
                .downField("value")
                .as[QuantityDataValue]
                .left
                .map(_.withMessage("Could not decode QuantityDataValue"))
            case "time" =>
              cursor
                .downField("value")
                .as[TimeDataValue]
                .left
                .map(_.withMessage("Could not decode TimeDataValue"))
            case x =>
              Left(DecodingFailure(s"Unexpected datatype = $x", cursor.history))
          }
        case None =>
          Left(
            DecodingFailure(
              "Could not find type field for datatype",
              cursor.history
            )
          )
      }
    }

  implicit final val encoder: Encoder[DataValue] = Encoder.instance {
    case x: StringDataValue           => x.asJson
    case x: WikibaseEntityIdDataValue => x.asJson
    case x: GlobeCoordinateDataValue  => x.asJson
    case x: QuantityDataValue         => x.asJson
    case x: TimeDataValue             => x.asJson
    case x: MonolingualTextDataValue  => x.asJson
  }

  implicit final val codec: Codec[DataValue] =
    Codec.from(datavalueDecoder, encoder)

}

sealed trait DataValue

@JsonCodec
case class StringDataValue(
  value: String,
  `type`: String)
    extends DataValue

@JsonCodec
case class MonolingualTextDataValue(
  text: String,
  language: String)
    extends DataValue

@JsonCodec
case class WikibaseEntityIdDataValue(
  value: WikibaseEntityId,
  `type`: String)
    extends DataValue

@JsonCodec case class WikibaseEntityId(
  `entity-type`: String,
  id: String,
  `numeric-id`: Option[Int])

object StringOrDouble {
  implicit final val decoder: Decoder[StringOrDouble] = Decoder.instance {
    cursor =>
      cursor.value match {
        case x if x.isString =>
          Try(x.asString.get.toDouble) match {
            case Failure(_) =>
              Left(DecodingFailure("Could not parse to double", cursor.history))
            case Success(value) =>
              Right(new StringOrDouble {
                override val get: Double = value
              })
          }
        case x if x.isNumber =>
          Right(new StringOrDouble {
            override val get: Double = x.asNumber.get.toDouble
          })
        case _ =>
          Left(
            DecodingFailure(
              "Could not decode to string or double",
              cursor.history
            )
          )
      }
  }

  implicit final val codec: Codec[StringOrDouble] =
    Codec.from(decoder, Encoder.encodeString.contramap(_.get.toString))
}

sealed trait StringOrDouble {
  def get: Double
}

@JsonCodec case class GlobeCoordinateDataValue(
  latitude: StringOrDouble,
  longitude: StringOrDouble,
  precision: Option[StringOrDouble],
  globe: String)
    extends DataValue

@JsonCodec case class QuantityDataValue(
  amount: StringOrDouble,
  upperBound: Option[StringOrDouble],
  lowerBound: Option[StringOrDouble],
  unit: String)
    extends DataValue

private object CustomFormatters {
  implicit final val offsetDateTimeParser: Codec[OffsetDateTime] =
    Codec.from(
      Decoder.decodeOffsetDateTimeWithFormatter(
        OffsetDateTimeUtils.SignedFormatter
      ),
      Encoder.encodeOffsetDateTimeWithFormatter(
        OffsetDateTimeUtils.SignedFormatter
      )
    )
}

import CustomFormatters._

@JsonCodec
case class TimeDataValue(
  time: String, // Make this more precise
  precision: Option[Int])
    extends DataValue

@JsonCodec
case class Reference(
  hash: String,
  snaks: Map[String, List[Snak]],
  `snaks-order`: List[String]) {

  def hasWikibaseIdReference(
    property: String,
    idValue: String
  ): Boolean = {
    snaks
      .get(property)
      .exists(_.exists(_.datavalue.collect {
        case WikibaseEntityIdDataValue(value, _) => value.id == idValue
      }.isDefined))
  }
}
