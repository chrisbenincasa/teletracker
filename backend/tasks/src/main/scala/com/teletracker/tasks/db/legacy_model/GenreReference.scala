package com.teletracker.tasks.db.legacy_model

import com.teletracker.common.db.model.ExternalSource

object GenreReference {
  def fromLine(
    line: String,
    separator: Char = '\t'
  ) = {
    val Array(id, externalSource, externalId, genreId) = line.split(separator)

    GenreReference(
      id = id.toInt,
      externalSource = ExternalSource.fromString(externalSource),
      externalId = externalId,
      genreId = genreId.toInt
    )
  }
}

case class GenreReference(
  id: Int,
  externalSource: ExternalSource,
  externalId: String,
  genreId: Int)
