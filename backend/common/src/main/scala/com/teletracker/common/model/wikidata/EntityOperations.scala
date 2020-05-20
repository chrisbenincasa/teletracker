package com.teletracker.common.model.wikidata

object EntityOperations {
  def extractTitle(
    entity: Entity,
    language: String = "en"
  ) = {
    entity.labels.get(language).map(_.value)
  }

  def extractValues(
    entity: Entity,
    property: String
  ) = {
    entity.claims
      .get(property)
      .map(claims => {
        claims.map(_.mainsnak).filter(_.datavalue.isDefined)
      })
  }
}
