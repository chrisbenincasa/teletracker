package com.teletracker.common.elasticsearch.util

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.EsAvailability.AvailabilityKey
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

  def applyAvailabilityDelta(
    esItem: EsItem,
    newAvailabilities: Iterable[EsAvailability],
    removedAvailabilities: Set[AvailabilityKey]
  ): Iterable[EsAvailability] = {
    esItem.availability match {
      case Some(_) =>
        val keyedUpdated = newAvailabilities
          .map(av => EsAvailability.getKey(av) -> av)
          .toMap
        val updatedAvailability = esItem.availabilityByKey
          .filterKeys(!removedAvailabilities.contains(_)) ++ keyedUpdated

        updatedAvailability.values

      case None =>
        newAvailabilities
    }
  }

  def removeAvailabilitiesForNetworks(
    esItem: EsItem,
    networks: Set[StoredNetwork]
  ) = {
    val ids = networks.map(_.id)
    esItem.availability match {
      case Some(value) =>
        esItem.copy(
          availability =
            Some(value.filterNot(av => ids.contains(av.network_id)))
        )
      case None => esItem
    }
  }

  def applyExternalIds(
    esItem: EsItem,
    externalIds: Map[ExternalSource, String]
  ): EsItem = {
    val newExternalIds = esItem.externalIdsGrouped ++ externalIds
    esItem.copy(external_ids = EsExternalId.fromMap(newExternalIds))
  }
}
