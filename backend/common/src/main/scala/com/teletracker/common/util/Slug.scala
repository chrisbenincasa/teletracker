package com.teletracker.common.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.{Locale, Objects}
import java.util.regex.Pattern
import scala.util.Try

object Slug {
  final private val Separator = "-"

  private val NonLatin = Pattern.compile("[^\\w-]")
  private val Whitespace = Pattern.compile("[\\s]")
  private val NormalizerFunc = Seq[String => String](
    _.replaceAll("&", "and"),
    _.replaceAll("-", ""),
    _.replaceAll("\\s{2,}", " "),
    Whitespace.matcher(_).replaceAll("-"),
    _.replaceAll("--", "-"), // This shouldn't happen... but we'll check
    Normalizer.normalize(_, Form.NFD),
    NonLatin.matcher(_).replaceAll(""),
    _.toLowerCase(Locale.ENGLISH)
  ).reduce(_.andThen(_))

  def apply(
    input: String,
    year: Int
  ): Slug = {
    val slug = NormalizerFunc(input)
    raw(s"$slug$Separator$year")
  }

  def apply(
    input: String,
    year: Option[Int]
  ): Slug = {
    year.map(apply(input, _)).getOrElse(forString(input))
  }

  def forString(input: String): Slug = new Slug(NormalizerFunc(input))

  def raw(input: String): Slug = new Slug(input)

  def unapply(arg: Slug): Option[(String, Option[Int])] = {
    val slugString = arg.toString
    val idx = slugString.lastIndexOf(Separator)
    if (idx > -1) {
      val (left, right) = slugString.splitAt(idx)
      Some(
        Try(right.stripPrefix(Separator).toInt)
          .map(year => left -> Some(year))
          .getOrElse(slugString -> None)
      )
    } else {
      Some(slugString -> None)
    }
  }
}

//@JsonCreator
class Slug(val value: String) extends AnyVal {
  @JsonValue
  override def toString: String = value

  def addSuffix(suffix: String) = new Slug(value + Slug.Separator + suffix)
}
