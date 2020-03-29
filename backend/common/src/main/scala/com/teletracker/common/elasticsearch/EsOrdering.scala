package com.teletracker.common.elasticsearch

import com.teletracker.common.util.Lists

object EsOrdering {
  final val forItemCastMember =
    (left: EsItemCastMember, right: EsItemCastMember) => {
      left.order <= right.order
    }

  final private val crewOrdering = Ordering
    .Tuple4[Option[Int], Option[String], Option[String], String](
      Lists.NullsLastOrdering[Int],
      Lists.NullsLastOrdering[String],
      Lists.NullsLastOrdering[String],
      Ordering[String]
    )

  val forItemCrewMember = (left: EsItemCrewMember, right: EsItemCrewMember) => {
    crewOrdering.lteq(
      (left.order, left.department, left.job, left.name),
      (right.order, right.department, right.job, right.name)
    )
  }
}
