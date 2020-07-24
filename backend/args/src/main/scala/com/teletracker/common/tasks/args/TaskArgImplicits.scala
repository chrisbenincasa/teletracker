package com.teletracker.common.tasks.args

import com.teletracker.common.db.model.ItemType
import scala.util.{Failure, Success, Try}

trait TaskArgImplicits {
  implicit def asRichArgs(args: Map[String, Any]): RichArgs =
    new RichArgs(args)
}

class RichArgs(val args: Map[String, Any]) extends AnyVal {
  def parse[T](implicit argParser: ArgParser[T]): Try[T] = argParser.parse(args)

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
        argParser.parse(ArgValue(value.get)).map(Some(_))
      case Some(value) => argParser.parse(ArgValue(value)).map(Some(_))
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
  def itemType: ItemType = valueOrThrow[ItemType]("itemType")
}
