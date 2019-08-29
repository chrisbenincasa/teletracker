package com.teletracker.common.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.{Locale, Objects}
import java.util.regex.Pattern

object Slug {
  private val NonLatin = Pattern.compile("[^\\w-]")
  private val Whitespace = Pattern.compile("[\\s]")
  private val NormalizerFunc = Seq[String => String](
    _.replaceAll("-", ""),
    _.replaceAll("\\s{2,}", " "),
    Whitespace.matcher(_).replaceAll("-"),
    Normalizer.normalize(_, Form.NFD),
    NonLatin.matcher(_).replaceAll(""),
    _.toLowerCase(Locale.ENGLISH)
  ).reduce(_.andThen(_))

  def apply(
    input: String,
    year: Int
  ): Slug = {
    val slug = NormalizerFunc(input)
    raw(s"$slug-$year")
  }

  def apply(
    input: String,
    year: Option[Int]
  ): Slug = {
    year.map(apply(input, _)).getOrElse(forString(input))
  }

  def forString(input: String): Slug = new Slug(NormalizerFunc(input))

  def raw(input: String): Slug = new Slug(input)
}

//@JsonCreator
class Slug(val value: String) extends AnyVal {
  @JsonValue
  override def toString: String = value
}
