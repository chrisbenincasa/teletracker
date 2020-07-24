package com.teletracker.common.tasks.args

import java.io.File
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import scala.util.{Failure, Success, Try}

trait SuperLowPriArgParsers {
  def anyArg[T: Manifest]: ArgParser[T] = build(cast[T])

  protected def build[T](parseFunc: Any => Try[T]): ArgParser[T] =
    new ValueArgParser[T] {
      override def parseValue(in: Any): Try[T] = parseFunc(in)
    }

  protected def cast[T: Manifest](x: Any): Try[T] = x match {
    case t: T => Success(t)
    case _ =>
      Failure(
        new IllegalArgumentException(
          s"Could not cast: $x to ${manifest[T].runtimeClass}"
        )
      )
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

trait LowPriArgParsers extends SuperLowPriArgParsers {
  implicit def optArg[T](implicit parser: ArgParser[T]): ArgParser[Option[T]] =
    new ValueArgParser[Option[T]] {

      override def parseOptValue(in: Option[Any]): Try[Option[T]] = in match {
        case Some(value) => parseValue(value)
        case None        => Success(None)
      }

      override def parseValue(in: Any): Try[Option[T]] =
        parser.parse(ArgValue(in)).map(Some(_))
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
            inner.parse(ArgValue(str)) match {
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

  final val tryInstance = cats.instances.try_.catsStdInstancesForTry
}

trait ArgParser[T] { self =>
  def parse(in: Map[String, Any]): Try[T] = parse(AllArgs(in))

  def parse(in: ArgType): Try[T]

  def andThen[U](parser: T => U): ArgParser[U] = new ArgParser[U] {
    override def parse(in: ArgType): Try[U] = self.parse(in).map(parser)
  }

  def andThenOpt[U](parser: T => Option[U]): ArgParser[U] = new ArgParser[U] {
    override def parse(in: ArgType): Try[U] =
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
    override def parse(in: ArgType): Try[T] =
      self.parse(in).orElse(parser.parse(in))
  }
}

trait ValueArgParser[T] extends ArgParser[T] {
  override def parse(in: ArgType): Try[T] = {
    in match {
      case AllArgs(_) =>
        Failure(
          new IllegalArgumentException("Expected arg value, got all args.")
        )
      case ArgValue(value: Option[_]) => parseOptValue(value)
      case ArgValue(value)            => parseValue(value)
    }
  }

  def parseOptValue(in: Option[Any]): Try[T] = {
    in match {
      case x if x.isDefined => parseValue(x.get)
      case x if x.isEmpty   => Failure(new IllegalArgumentException("Empty"))
    }
  }

  def parseValue(in: Any): Try[T]
}

sealed trait ArgType
case class AllArgs(args: Map[String, Any]) extends ArgType
case class ArgValue(value: Any) extends ArgType
