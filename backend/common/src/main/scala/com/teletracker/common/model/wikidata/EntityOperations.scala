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
    property: String,
    claimFilter: Claim => Boolean = _ => true
  ) = {
    entity.claims
      .get(property)
      .map(claims => {
        claims.filter(claimFilter).map(_.mainsnak).filter(_.datavalue.isDefined)
      })
  }
}
