package com.teletracker.common.util.json.circe

import io.circe.syntax._
import io.circe.{Encoder, Printer}

object JsonExtensions extends JsonExtensions {
  final val indentedPrinter = Printer.spaces4.copy(dropNullValues = true)
  final val compactPrinter = Printer.noSpaces.copy(dropNullValues = true)
}

trait JsonExtensions {
  implicit def toRichJsonable[T: Encoder](t: T): RichJsonable[T] =
    new RichJsonable[T](t)
}

final class RichJsonable[T](val t: T) extends AnyVal {
  def print(compact: Boolean)(implicit enc: Encoder[T]): String =
    if (compact) printCompact
    else t.asJson.printWith(JsonExtensions.indentedPrinter)

  def printCompact(implicit enc: Encoder[T]): String =
    t.asJson.printWith(JsonExtensions.compactPrinter)
}
