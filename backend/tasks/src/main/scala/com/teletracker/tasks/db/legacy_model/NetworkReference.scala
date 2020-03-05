package com.teletracker.tasks.db.legacy_model

import com.teletracker.common.db.model.ExternalSource

object NetworkReference {
  def fromLine(
    line: String,
    separator: Char = '\t'
  ): NetworkReference = {
    val Array(id, externalSource, externalId, networkId) = line.split(separator)

    NetworkReference(
      id = id.toInt,
      externalSource = ExternalSource.fromString(externalSource),
      externalId = externalId,
      networkId = networkId.toInt
    )
  }
}

case class NetworkReference(
  id: Int,
  externalSource: ExternalSource,
  externalId: String,
  networkId: Int)
