package com.teletracker.common.elasticsearch.util

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsExternalId,
  EsItem
}

object ItemUpdateApplier {
  def applyAvailabilities(
    esItem: EsItem,
    availabilities: Seq[EsAvailability]
  ): EsItem = {
    val newAvailabilities = esItem.availability match {
      case Some(value) =>
        val duplicatesRemoved = value.filterNot(availability => {
          availabilities.exists(
            EsAvailability.availabilityEquivalent(_, availability)
          )
        })

        duplicatesRemoved ++ availabilities

      case None =>
        availabilities
    }

    esItem.copy(
      availability = Some(newAvailabilities.toList)
    )
  }

  def applyExternalIds(
    esItem: EsItem,
    externalIds: Map[ExternalSource, String]
  ): EsItem = {
    val newExternalIds = esItem.externalIdsGrouped ++ externalIds
    esItem.copy(external_ids = EsExternalId.fromMap(newExternalIds))
  }
}
