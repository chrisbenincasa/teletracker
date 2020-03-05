package com.teletracker.tasks.db.legacy_model

import com.teletracker.common.util.Slug

object Network {
  def fromLine(
    line: String,
    separator: Char = '\t'
  ) = {
    val Array(id, name, slug, shortname, homepage, origin) =
      line.split(separator)

    Network(
      id = id.toInt,
      name = name,
      slug = Slug.raw(slug),
      shortname = shortname,
      homepage = Option(homepage).filterNot(_ == "\\N"),
      origin = Option(origin).filterNot(_ == "\\N")
    )
  }
}

case class Network(
  id: Int,
  name: String,
  slug: Slug,
  shortname: String,
  homepage: Option[String],
  origin: Option[String])
