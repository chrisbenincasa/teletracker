package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.elasticsearch.{EsItemCastMember, EsItemCrewMember}
import com.teletracker.common.pubsub.TaskScheduler
import com.teletracker.common.util.Tuples._
import javax.inject.Inject
import scala.concurrent.Future

class ItemCreditsDenormalizationHelper @Inject()(taskScheduler: TaskScheduler) {
  def castNeedsDenormalization(
    newCast: Option[List[EsItemCastMember]],
    existingCast: Option[List[EsItemCastMember]]
  ): Boolean = {
    val newAndOld = (newCast, existingCast)

    if (newAndOld.bothEmpty) {
      false
    } else {
      newAndOld
        .ifBothPresent((newCast, oldCast) => {
          val newById = newCast.map(member => member.id -> member).toMap
          val oldById = oldCast.map(member => member.id -> member).toMap

          val diff =
            ((newById.keySet diff oldById.keySet) union (oldById.keySet diff newById.keySet)).nonEmpty

          diff || {
            newById.keys.exists(id => {
              val newMember = newById(id)
              val oldMember = oldById(id)

              castMemberNeedsDenorm(newMember, oldMember)
            })
          }
        })
        .getOrElse(false)
    }
  }

  def crewNeedsDenormalization(
    newCrew: Option[List[EsItemCrewMember]],
    existingCrew: Option[List[EsItemCrewMember]]
  ): Boolean = {
    val newAndOld = (newCrew, existingCrew)

    if (newAndOld.bothEmpty) {
      false
    } else {
      newAndOld
        .ifBothPresent((newCrew, oldCrew) => {
          val newById = newCrew.map(member => member.id -> member).toMap
          val oldById = oldCrew.map(member => member.id -> member).toMap

          val diff =
            ((newById.keySet diff oldById.keySet) union (oldById.keySet diff newById.keySet)).nonEmpty

          diff || {
            newById.keys.exists(id => {
              val newMember = newById(id)
              val oldMember = oldById(id)

              crewMemberNeedsDenorm(newMember, oldMember)
            })
          }
        })
        .getOrElse(false)
    }
  }

  private def castMemberNeedsDenorm(
    left: EsItemCastMember,
    right: EsItemCastMember
  ) = {
    left.character != right.character ||
    left.slug != right.slug
  }

  private def crewMemberNeedsDenorm(
    left: EsItemCrewMember,
    right: EsItemCrewMember
  ) = {
    left.job != right.job ||
    left.department != right.department ||
    left.slug != right.slug ||
    left.name != right.name
  }
}
