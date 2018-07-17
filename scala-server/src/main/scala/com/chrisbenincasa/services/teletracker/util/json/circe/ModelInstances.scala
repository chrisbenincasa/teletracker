package com.chrisbenincasa.services.teletracker.util.json.circe

import com.chrisbenincasa.services.teletracker.db.model._
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

  implicit val availabilityEncoder = deriveEncoder[Availability]

  implicit val availabilityDecoder = deriveDecoder[Availability]

  implicit val objectMetadataEncoder = deriveEncoder[ObjectMetadata]

  implicit val objectMetadataDecoder = deriveDecoder[ObjectMetadata]

  implicit val thingEncoder = deriveEncoder[Thing]

  implicit val thingDecoder = deriveDecoder[Thing]

  implicit val thingWithDetailsEncoder = deriveEncoder[ThingWithDetails]

  implicit val thingWithDetailsDecoder = deriveDecoder[ThingWithDetails]

  implicit val userDecoder = deriveDecoder[User]
}
