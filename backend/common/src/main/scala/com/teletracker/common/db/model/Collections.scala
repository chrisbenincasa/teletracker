package com.teletracker.common.db.model

import java.util.UUID

case class Collection(
  id: Int,
  name: String,
  overview: Option[String],
  tmdb_id: Option[String])

case class CollectionThing(
  id: Int,
  collectionId: Int,
  thingId: UUID)
