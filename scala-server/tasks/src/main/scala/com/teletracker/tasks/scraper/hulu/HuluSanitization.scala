package com.teletracker.tasks.scraper.hulu

object HuluSanitization {
  private val endsWithNote = "\\([A-z0-9]+\\)$".r
  private val badApostrophe = "[A-z]\\?[A-z]".r

  def sanitizeTitle(title: String): String = {
    val withoutNote = endsWithNote.replaceAllIn(title, "")
    badApostrophe
      .findAllIn(
        withoutNote
      )
      .foldLeft(withoutNote)((str, part) => {
        val replacement = part.replaceAllLiterally("?", "'")
        str.replaceAllLiterally(part, replacement)
      })
  }
}
