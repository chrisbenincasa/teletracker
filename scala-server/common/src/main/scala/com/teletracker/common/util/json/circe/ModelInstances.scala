package com.teletracker.common.util.json.circe

import com.teletracker.common.db.model._
import com.teletracker.common.util.Slug
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.generic.auto._
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import scala.reflect.{classTag, ClassTag}

trait ModelInstances extends JodaInstances {
  implicit def javaEnumDecoder[A <: Enum[A]: ClassTag]: Decoder[A] =
    Decoder.instance { a =>
      a.focus match {
        case Some(v) if v.isString =>
          classTag[A].runtimeClass
            .asInstanceOf[Class[A]]
            .getEnumConstants
            .find(_.toString().equalsIgnoreCase(v.asString.get)) match {
            case Some(v2) => Right(v2)
            case None =>
              Left(
                DecodingFailure(s"Could not find value ${v} in enum", a.history)
              )
          }
        case Some(_) => Left(DecodingFailure("???", a.history))
        case None    => Left(DecodingFailure("???", a.history))
      }
    }

  implicit def javaEnumEncoder[A <: Enum[A]]: Encoder[A] = Encoder.instance {
    a =>
      Json.fromString(a.toString)
  }

  implicit val slugEncoder: Encoder[Slug] =
    Encoder.encodeString.contramap(slug => slug.value)
  implicit val slugDecoder: Decoder[Slug] = Decoder.decodeString.map(Slug.raw)

  implicit val availabilityEncoder: Codec[Availability] = deriveCodec
  implicit val networkEncoder: Codec[Network] = deriveCodec
  implicit val objectMetadataEncoder: Codec[ObjectMetadata] = deriveCodec
  implicit val thingEncoder: Codec[Thing] = deriveCodec
  implicit val partialThingEncoder: Codec[PartialThing] = deriveCodec
  implicit val trackedListEncoder: Codec[TrackedList] = deriveCodec
  implicit val userPrefsEncoder: Codec[UserPreferences] = deriveCodec
//  implicit val userEncoder: Codec[UserDetails] = deriveCodec
  implicit val dynamicListRulesEncoder: Codec[DynamicListRules] = deriveCodec
  implicit val availabilityWithDetailsEncoder: Codec[AvailabilityWithDetails] =
    deriveCodec
  implicit val dynamicListTagRuleEncoder: Codec[DynamicListTagRule] =
    deriveCodec
}
