package com.teletracker.common.elasticsearch

import com.teletracker.common.util.Lists

object EsOrdering {
  final val forItemCastMember =
    (left: EsItemCastMember, right: EsItemCastMember) => {
      left.order <= right.order
    }

  val forItemCrewMember = (left: EsItemCrewMember, right: EsItemCrewMember) => {
    Lists.NullsLastOrdering[Int].lteq(left.order, right.order)
  }
}
