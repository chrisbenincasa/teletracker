package com.teletracker.common.db.dynamo.util

import com.teletracker.common.db.dynamo.util.syntax.EpochSeconds
import io.circe.{Codec, Json}
import shapeless.tag
import shapeless.tag.@@
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant
import java.util.UUID
import scala.collection.JavaConverters._

trait AsAttributeValue[T] {
  def to(value: T): AttributeValue
}

trait FromAttributeValue[T] {
  def from(value: AttributeValue): T
}

trait ToAndFromAttributeValue[T]
    extends AsAttributeValue[T]
    with FromAttributeValue[T]

trait LowPriAsAttributeValueInstances {
  protected def make[T](
    toFn: T => AttributeValue,
    fromFn: AttributeValue => T
  ): ToAndFromAttributeValue[T] =
    new ToAndFromAttributeValue[T] {
      override def to(value: T): AttributeValue = toFn(value)
      override def from(value: AttributeValue): T = fromFn(value)
    }

  implicit def typedJsonAsAttributeValue[T](
    implicit codec: Codec[T]
  ): ToAndFromAttributeValue[T] = make(
    value => AttributeValue.builder().s(codec(value).noSpaces).build(),
    value => io.circe.parser.decode[T](value.s()).fold(throw _, identity)
  )
}

trait AsAttributeValueInstances extends LowPriAsAttributeValueInstances {
  implicit val stringAsAttributeValue: ToAndFromAttributeValue[String] = make(
    value => AttributeValue.builder().s(value).build(),
    value => value.s()
  )

  implicit val intAsAttributeValue: ToAndFromAttributeValue[Int] = make(
    value => AttributeValue.builder().n(value.toString).build(),
    value => value.n().toInt
  )

  implicit val longAsAttributeValue: ToAndFromAttributeValue[Long] = make(
    value => AttributeValue.builder().n(value.toString).build(),
    value => value.n().toLong
  )

  implicit val boolAsAttributeValue: ToAndFromAttributeValue[Boolean] = make(
    value => AttributeValue.builder().bool(value).build(),
    value => value.bool()
  )

  implicit val uuidAsAttributeValue: ToAndFromAttributeValue[UUID] = make(
    value => AttributeValue.builder().s(value.toString).build(),
    value => UUID.fromString(value.s())
  )

  implicit val instantAsAttributeValue: ToAndFromAttributeValue[Instant] = make(
    value => AttributeValue.builder().s(value.toString).build(),
    value => Instant.parse(value.s())
  )

  implicit val instantFromSecondsAsAttributeValue
    : ToAndFromAttributeValue[Instant @@ EpochSeconds] = make(
    value =>
      AttributeValue.builder().n((value.toEpochMilli / 1000L).toString).build(),
    value => tag[EpochSeconds](Instant.ofEpochSecond(value.n().toLong))
  )

  implicit val stringSetAsAttributeValue: ToAndFromAttributeValue[Set[String]] =
    make(
      value => AttributeValue.builder().ss(value.asJavaCollection).build(),
      value => value.ss().asScala.toSet
    )

  implicit val jsonAsAttributeValue: ToAndFromAttributeValue[Json] =
    make(
      value => AttributeValue.builder().s(value.noSpaces).build(),
      value =>
        io.circe.parser
          .parse(value.s())
          .fold(throw _, identity) // TODO don't do this
    )

  implicit def optionAsAttributeValue[T](
    implicit other: ToAndFromAttributeValue[T]
  ): ToAndFromAttributeValue[Option[T]] =
    make(
      value =>
        value
          .map(other.to)
          .getOrElse(AttributeValue.builder().nul(true).build()),
      value => if (value.nul()) None else Some(other.from(value))
    )
}

object AsAttributeValue extends AsAttributeValueInstances

object syntax extends AsAttributeValueInstances {
  sealed trait EpochSeconds

  implicit def toAsAttributeValue[T](
    value: T
  )(implicit aav: AsAttributeValue[T]
  ): AsAttributeValueOps[T] = new AsAttributeValueOps[T](value)

  implicit def toFromAttributeValueOps(
    attributeValue: AttributeValue
  ): FromAttributeValueOps =
    new FromAttributeValueOps(attributeValue)
}

final class AsAttributeValueOps[T](val value: T) extends AnyVal {
  def toAttributeValue(implicit aav: AsAttributeValue[T]): AttributeValue =
    aav.to(value)

  def toAttributeValueOfType[U](
    m: T => U
  )(implicit aav: AsAttributeValue[U]
  ): AttributeValue =
    aav.to(m(value))

  def toAttributeValueTagged[U](
    implicit aav: AsAttributeValue[T @@ U]
  ): AttributeValue =
    toAttributeValueOfType[T @@ U](tag[U](_))
}

final class FromAttributeValueOps(val value: AttributeValue) extends AnyVal {
  def fromAttributeValue[T](
    implicit fromAttributeValue: FromAttributeValue[T]
  ): T =
    fromAttributeValue.from(value)
}
