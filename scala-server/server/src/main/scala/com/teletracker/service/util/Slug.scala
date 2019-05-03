package com.teletracker.service.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.Locale
import java.util.regex.Pattern

object Slug {
  private val NonLatin = Pattern.compile("[^\\w-]")
  private val Whitespace = Pattern.compile("[\\s]")

  def apply(input: String): Slug = {
    val nowhitespace = Whitespace.matcher(input).replaceAll("-")
    val normalized = Normalizer.normalize(nowhitespace, Form.NFD)
    val slug = NonLatin.matcher(normalized).replaceAll("")
    new Slug(slug.toLowerCase(Locale.ENGLISH))
  }

  @JsonCreator
  def raw(input: String): Slug = new Slug(input)
}

case class Slug private (value: String) extends AnyVal {
  @JsonValue
  override def toString: String = value
}