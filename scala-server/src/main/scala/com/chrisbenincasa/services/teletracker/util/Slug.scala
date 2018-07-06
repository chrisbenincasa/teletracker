package com.chrisbenincasa.services.teletracker.util

import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.Locale
import java.util.regex.Pattern

object Slug {
  private val NonLatin = Pattern.compile("[^\\w-]")
  private val Whitespace = Pattern.compile("[\\s]")

  def apply(input: String): String = {
    val nowhitespace = Whitespace.matcher(input).replaceAll("-")
    val normalized = Normalizer.normalize(nowhitespace, Form.NFD)
    val slug = NonLatin.matcher(normalized).replaceAll("")
    slug.toLowerCase(Locale.ENGLISH)
  }
}
