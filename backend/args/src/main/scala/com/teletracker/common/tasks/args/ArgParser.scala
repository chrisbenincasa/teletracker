package com.teletracker.common.tasks.args

import com.teletracker.common.tasks.args.ArgParser.{
  anyArg,
  doubleArg,
  stringArg
}
import shapeless.tag
import shapeless.tag.@@
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import scala.util.{Failure, Success, Try}

trait ArgParserBuilders {
  def anyArg[T: Manifest]: ArgParser[T] = build(cast[T])

  protected[args] def build[T](parseFunc: Any => Try[T]): ArgParser[T] =
    new ValueArgParser[T] {
      override def parseValue(in: Any): Try[T] = parseFunc(in)
    }

  protected[args] def cast[T: Manifest](x: Any): Try[T] = x match {
    case t: T => Success(t)
    case _ =>
      Failure(
        new IllegalArgumentException(
          s"Could not cast: $x to ${manifest[T].runtimeClass}"
        )
      )
  }

  protected[args] def tryX[T: Manifest](f: (Any => T)*): Any => Try[T] = { in =>
    f.toStream
      .map(func => Try(func(in)))
      .find(_.isSuccess) match {
      case Some(success) => success
      case None          => cast[T](in)
    }
  }
}

object ArgParserBuilders extends ArgParserBuilders

class OptArgParser[T](parser: ArgParser[T]) extends ValueArgParser[Option[T]] {
  override def parseOptValue(in: Option[Any]): Try[Option[T]] = in match {
    case None | Some(None) => Success(None)
    case Some(value)       => parseValue(value)
  }

  override def parseValue(in: Any): Try[Option[T]] =
    parser.parse(ArgValue(in)).map(Some(_))
}

trait LowPriArgParsers extends ArgParserBuilders {
  implicit def optArg[T](implicit parser: ArgParser[T]): ArgParser[Option[T]] =
    new OptArgParser(parser)
}

object ArgParser extends LowPriArgParsers {
  import scala.concurrent.duration._

  sealed trait Millis

  implicit val stringArg: ArgParser[String] = anyArg[String]

  implicit val doubleArg: ArgParser[Double] =
    anyArg[Double].or(stringArg andThen (_.toDouble))

  implicit val floatArg: ArgParser[Float] =
    anyArg[Float]
      .or(doubleArg andThen (_.toFloat))
      .or(stringArg andThen (_.toFloat))

  implicit val longArg: ArgParser[Long] =
    anyArg[Long]
      .or(doubleArg andThen (_.toLong))

  implicit val intArg: ArgParser[Int] =
    anyArg[Int]
      .or(doubleArg andThen (_.toInt))
      .or(longArg andThen (_.toInt))
      .or(stringArg andThen (_.toInt))

  implicit val uriArg: ArgParser[URI] =
    stringArg.andThen(URI.create).or(anyArg[URI])

  implicit val fileArg: ArgParser[File] =
    uriArg.andThen(new File(_)).or(build(tryX(_.asInstanceOf[File]))).or(anyArg)

  implicit val booleanArg: ArgParser[Boolean] = anyArg[Boolean].or(
    stringArg
      .andThen(
        new java.lang.Boolean(
          _
        ).booleanValue()
      )
      .or(anyArg[Boolean])
  )

  implicit val uuidArg: ArgParser[UUID] =
    stringArg.andThen(UUID.fromString).or(anyArg)

  implicit val localDateArg: ArgParser[LocalDate] =
    stringArg.andThen(LocalDate.parse).or(anyArg)

  implicit val finiteDurationMillisArg: ArgParser[FiniteDuration @@ Millis] =
    longArg.andThen(l => tag[Millis](l millis)).or(anyArg)

  implicit def javaEnumArg[T <: Enum[T]: Manifest]: ArgParser[T] = {
    val enums = manifest[T].runtimeClass.asInstanceOf[Class[T]].getEnumConstants

    stringArg
      .andThenOpt(str => enums.find(_.name().equalsIgnoreCase(str)))
      .or(anyArg)
  }

  implicit def listArg[T: Manifest](
    implicit inner: ArgParser[T]
  ): ArgParser[List[T]] = {
    stringArg
      .andThen(
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
      .or(anyArg[List[T]])
  }

  implicit def setArg[T: Manifest](
    implicit inner: ArgParser[T]
  ): ArgParser[Set[T]] =
    listArg[T].andThen(_.toSet).or(anyArg[Set[T]])

  implicit val unitArg: ArgParser[Unit] =
    (_: ArgType) => Success(Unit)

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
      case x if x.isEmpty =>
        Failure(
          new IllegalArgumentException(
            s"Trying to parse: $in, but it was empty"
          )
        )
    }
  }

  def parseValue(in: Any): Try[T]
}

sealed trait ArgType
case class AllArgs(args: Map[String, Any]) extends ArgType
case class ArgValue(value: Any) extends ArgType
