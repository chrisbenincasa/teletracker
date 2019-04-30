package com.chrisbenincasa.services.teletracker.util.json.circe

import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.util.Slug
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.generic.auto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import scala.reflect.{ClassTag, classTag}

trait ModelInstances extends JodaInstances {
  implicit def javaEnumDecoder[A <: Enum[A] : ClassTag]: Decoder[A] = Decoder.instance { a =>
    a.focus match {
      case Some(v) if v.isString =>
        classTag[A].runtimeClass.asInstanceOf[Class[A]].getEnumConstants.find(_.name().equalsIgnoreCase(v.asString.get)) match {
          case Some(v2) => Right(v2)
          case None => Left(DecodingFailure(s"Could not find value ${v} in enum", a.history))
        }
      case Some(_) => Left(DecodingFailure("???", a.history))
      case None => Left(DecodingFailure("???", a.history))
    }
  }

  implicit def javaEnumEncoder[A <: Enum[A]]: Encoder[A] = Encoder.instance { a =>
    Json.fromString(a.toString)
  }

  implicit val slugEncoder: Encoder[Slug] = Encoder.encodeString.contramap(slug => slug.value)
  implicit val slugDecoder: Decoder[Slug] = Decoder.decodeString.map(Slug.raw)

  implicit val availabilityEncoder = deriveEncoder[Availability]
  implicit val availabilityDecoder = deriveDecoder[Availability]

  implicit val networkEncoder = deriveEncoder[Network]
  implicit val networkDecoder = deriveDecoder[Network]

  implicit val availabilityWithDetailsEncoder = deriveEncoder[AvailabilityWithDetails]
  implicit val availabilityWithDetailsDecoder = deriveDecoder[AvailabilityWithDetails]

  implicit val objectMetadataEncoder = deriveEncoder[ObjectMetadata]
  implicit val objectMetadataDecoder = deriveDecoder[ObjectMetadata]

  implicit val thingEncoder = deriveEncoder[Thing]
  implicit val thingDecoder = deriveDecoder[Thing]

  implicit val partialThingEncoder = deriveEncoder[PartialThing]
  implicit val partialThingDecoder = deriveDecoder[PartialThing]

  implicit val trackedListEncoder = deriveEncoder[TrackedList]
  implicit val trackedListDecoder = deriveDecoder[TrackedList]

  implicit val userDecoder = deriveDecoder[User]
  implicit val userEncoder = deriveEncoder[User]
}
