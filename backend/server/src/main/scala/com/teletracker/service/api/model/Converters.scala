package com.teletracker.service.api.model

object Converters {
  def dbPersonToEnrichedPerson(
    person: com.teletracker.common.db.model.Person
  ): EnrichedPerson = {
    EnrichedPerson(
      id = person.id,
      name = person.name,
      normalizedName = person.normalizedName,
      createdAt = person.createdAt,
      lastUpdatedAt = person.lastUpdatedAt,
      metadata = person.metadata,
      tmdbId = person.tmdbId,
      popularity = person.popularity,
      credits = None
    )
  }
}
