package com.teletracker.common.tasks

import io.circe.Json
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import scala.util.{Failure, Success, Try}

object TaskArgs extends TaskArgImplicits {
  def extractArgs(args: Map[String, Json]): Map[String, Option[Any]] = {
    args.mapValues(extractValue)
  }

  private def extractValue(j: Json): Option[Any] = {
    j.fold(
      None,
      Some(_),
      x => Some(x.toDouble),
      Some(_),
      v => Some(v.map(extractValue)),
      o => Some(o.toMap.mapValues(extractValue))
    )
  }
}

trait TaskArgImplicits {
  implicit def asRichArgs(args: Map[String, Any]): RichArgs =
    new RichArgs(args)
}

class RichArgs(val args: Map[String, Any]) extends AnyVal {
  def valueOrDefault[T](
    key: String,
    default: => T
  )(implicit argParser: ArgParser[T]
  ): T =
    value(key).getOrElse(default)

  def value[T](key: String)(implicit argParser: ArgParser[T]): Option[T] = {
    valueTry[T](key).toOption.flatten
  }

  def valueOrThrow[T](key: String)(implicit argParser: ArgParser[T]): T =
    valueTry[T](key) match {
      case Failure(exception) => throw exception
      case Success(None) =>
        throw new IllegalArgumentException(s"No argument under key: $key")
      case Success(Some(value)) => value
    }

  private def valueTry[T](
    key: String
  )(implicit argParser: ArgParser[T]
  ): Try[Option[T]] = {
    args.get(key) match {
      case Some(value: Option[_]) if value.isEmpty => Success(None)
      case Some(value: Option[_]) if value.isDefined =>
        argParser.parse(value.get).map(Some(_))
      case Some(value) => argParser.parse(value).map(Some(_))
      case None        => Success(None)
    }
  }

  def valueRequiredIfOtherPresent[T](
    key: String,
    requiredValue: Option[_]
  )(implicit argParser: ArgParser[T]
  ): Option[T] = {
    val theValue = value[T](key)
    if (theValue.isEmpty && requiredValue.isDefined) {
      throw new IllegalArgumentException(
        s"$key is required when linked value is required."
      )
    } else {
      theValue
    }
  }

  // Convenience functions
  def intOpt(key: String): Option[Int] = value[Int](key)
  def int(key: String): Int = valueOrThrow[Int](key)
  def int(
    key: String,
    default: => Int
  ): Int = valueOrDefault[Int](key, default)

  def stringOpt(key: String): Option[String] = value[String](key)
  def string(key: String): String = valueOrThrow[String](key)
  def string(
    key: String,
    default: => String
  ): String = valueOrDefault[String](key, default)

  // Special args
  def limit: Int = int("limit", -1)
  def dryRun: Boolean = valueOrDefault("dryRun", true)
}

trait LowPriArgParsers {
  implicit def anyArg[T: Manifest]: ArgParser[T] = build(cast[T])

  protected def build[T](parseFunc: Any => Try[T]): ArgParser[T] =
    new ArgParser[T] {
      override def parse(in: Any): Try[T] = parseFunc(in)
    }

  protected def cast[T: Manifest](x: Any): Try[T] = x match {
    case t: T => Success(t)
    case _    => Failure(new IllegalArgumentException)
  }

  protected def tryX[T: Manifest](f: (Any => T)*): Any => Try[T] = { in =>
    f.toStream
      .map(func => Try(func(in)))
      .find(_.isSuccess) match {
      case Some(success) => success
      case None          => cast[T](in)
    }

  }
}

object ArgParser extends LowPriArgParsers {
  implicit val stringArg: ArgParser[String] = anyArg[String]

  implicit val doubleArg: ArgParser[Double] =
    anyArg[Double].or(stringArg andThen (_.toDouble))

  implicit val intArg: ArgParser[Int] =
    anyArg[Int].or(doubleArg andThen (_.toInt)).or(stringArg andThen (_.toInt))

  implicit val uriArg: ArgParser[URI] =
    stringArg.andThen(new URI(_)).or(anyArg[URI])

  implicit val fileArg: ArgParser[File] =
    uriArg.andThen(new File(_)).or(build(tryX(_.asInstanceOf[File])))

  implicit val booleanArg: ArgParser[Boolean] = anyArg[Boolean].or(
    stringArg andThen (new java.lang.Boolean(
      _
    ))
  )

  implicit val uuidArg: ArgParser[UUID] =
    stringArg.andThen(UUID.fromString)

  implicit def javaEnumArg[T <: Enum[T]: Manifest]: ArgParser[T] = {
    val enums = manifest[T].runtimeClass.asInstanceOf[Class[T]].getEnumConstants

    stringArg.andThenOpt(str => enums.find(_.name().equalsIgnoreCase(str)))
  }

  implicit val localDateArg: ArgParser[LocalDate] =
    stringArg.andThen(LocalDate.parse)

  implicit def listArg[T](implicit inner: ArgParser[T]): ArgParser[List[T]] = {
    stringArg.andThen(
      _.split(',')
        .map(
          str =>
            inner.parse(str) match {
              case Failure(exception) => throw exception
              case Success(value)     => value
            }
        )
        .toList
    )
  }

  implicit def setArg[T](implicit inner: ArgParser[T]): ArgParser[Set[T]] = {
    listArg[T].andThen(_.toSet)
  }
}

trait ArgParser[T] { self =>
  def parse(in: Any): Try[T]

  def andThen[U](parser: T => U): ArgParser[U] = new ArgParser[U] {
    override def parse(in: Any): Try[U] = self.parse(in).map(parser)
  }

  def andThenOpt[U](parser: T => Option[U]): ArgParser[U] = new ArgParser[U] {
    override def parse(in: Any): Try[U] =
      self
        .parse(in)
        .flatMap(
          t =>
            parser(t) match {
              case Some(value) => Success(value)
              case None =>
                Failure(
                  new IllegalArgumentException(s"parser returned None for $in")
                )
            }
        )
  }

  def or(parser: ArgParser[T]): ArgParser[T] = new ArgParser[T] {
    override def parse(in: Any): Try[T] =
      self.parse(in).orElse(parser.parse(in))
  }
}
