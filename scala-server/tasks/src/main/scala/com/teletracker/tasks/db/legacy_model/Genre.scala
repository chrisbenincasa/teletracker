package com.teletracker.tasks.db.legacy_model

import com.teletracker.common.db.model.{ExternalSource, GenreType}
import com.teletracker.common.util.Slug

object Genre {
  def fromLine(
    line: String,
    separator: Char = '\t'
  ) = {
    val Array(id, name, slug, types) = line.split(separator)

    Genre(
      id = id.toInt,
      name = name,
      slug = Slug.raw(slug),
      `type` = types
        .stripPrefix("{")
        .stripSuffix("}")
        .split(",")
        .map(GenreType.fromString)
        .toList
    )
  }
}

case class Genre(
  id: Int,
  name: String,
  slug: Slug,
  `type`: List[GenreType])
