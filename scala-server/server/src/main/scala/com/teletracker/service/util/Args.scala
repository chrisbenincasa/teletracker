package com.teletracker.service.util

import java.net.URI
import scala.util.Try

object Args {
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

object ArgParser {
  implicit val booleanArg: ArgParser[Boolean] = build(
    tryX(
      _.asInstanceOf[Boolean],
      in => new java.lang.Boolean(in.asInstanceOf[String])
    )
  )

  implicit val stringArg: ArgParser[String] = build(_.asInstanceOf[String])

  implicit val intArg: ArgParser[Int] = build(
    tryX(_.asInstanceOf[Int], _.asInstanceOf[String].toInt)
  )

  implicit val uriArg: ArgParser[URI] = stringArg andThen (new URI(_))

  private def build[T](parseFunc: Any => T): ArgParser[T] = new ArgParser[T] {
    override def parse(in: Any): T = parseFunc(in)
  }

  private def tryX[T](f: (Any => T)*): Any => T = { in =>
    f.toStream.map(func => Try(func(in))).find(_.isSuccess).map(_.get).get
  }

}

trait ArgParser[T] { self =>
  def parse(in: Any): T
  def parseOpt(in: Any): Option[T] = in match {
    case None    => None
    case Some(v) => Try(parse(v)).toOption
    case v       => Try(parse(v)).toOption
  }

  def andThen[U](parser: T => U): ArgParser[U] = new ArgParser[U] {
    override def parse(in: Any): U = parser(self.parse(in))
  }
}
