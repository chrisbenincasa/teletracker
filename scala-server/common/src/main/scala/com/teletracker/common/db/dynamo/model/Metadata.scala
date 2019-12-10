package com.teletracker.common.db.dynamo.model

object MetadataType {
  final val NetworkType = "network"
  final val NetworkReferenceType = "network_reference"

  final val GenreType = "genre"
  final val GenreReferenceType = "genre_reference"

  final val UserPreferencesType = "user_preferences"
}

object MetadataFields {
  final val TypeField = "type"
}
