package com.teletracker.tasks.util

import java.io.File
import java.net.URI
import java.util.UUID
import scala.util.{Failure, Success, Try}

object Args extends Args

trait Args {
  implicit def asRichArgs(args: Map[String, Option[Any]]): RichArgs =
    new RichArgs(args)
}

class RichArgs(val args: Map[String, Option[Any]]) extends AnyVal {
  def valueOrDefault[T](
    key: String,
    default: => T
  )(implicit argParser: ArgParser[T]
  ): T =
    value(key).getOrElse(default)

  def value[T](key: String)(implicit argParser: ArgParser[T]): Option[T] =
    args.get(key).flatten.flatMap(argParser.parseOpt)

  def valueOrThrow[T](key: String)(implicit argParser: ArgParser[T]): T =
    value(key).getOrElse(
      throw new IllegalArgumentException(s"No argument under key: $key")
    )
}

trait LowPriArgParsers {
  implicit def anyArg[T: Manifest]: ArgParser[T] = build(cast[T])

  protected def build[T](parseFunc: Any => Try[T]): ArgParser[T] =
    new ArgParser[T] {
      override def parse(in: Any): Try[T] = parseFunc(in)
    }

  def cast[T: Manifest](x: Any): Try[T] = x match {
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
}

trait ArgParser[T] { self =>
  def parse(in: Any): Try[T]
  def parseOpt(in: Any): Option[T] = in match {
    case None    => None
    case Some(v) => parse(v).toOption
    case v       => parse(v).toOption
  }

  def andThen[U](parser: T => U): ArgParser[U] = new ArgParser[U] {
    override def parse(in: Any): Try[U] = self.parse(in).map(parser)
  }

  def or(parser: ArgParser[T]): ArgParser[T] = new ArgParser[T] {
    override def parse(in: Any): Try[T] =
      self.parse(in).orElse(parser.parse(in))
  }
}
