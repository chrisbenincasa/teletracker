package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.PersonAssociationType
import com.teletracker.common.elasticsearch.{
  BinaryOperator,
  PeopleCreditSearch,
  PersonCreditSearch
}
import com.teletracker.common.util.HasThingIdOrSlug

case class PeopleCreditsFilter(
  cast: Seq[String],
  crew: Seq[String],
  operator: BinaryOperator) {
  def nonEmpty: Boolean = cast.nonEmpty || crew.nonEmpty

  def toPeopleCreditSearch: Option[PeopleCreditSearch] =
    if (nonEmpty) {
      val castItems = cast
        .map(HasThingIdOrSlug.parseIdOrSlug)
        .map(PersonCreditSearch(_, PersonAssociationType.Cast))
      val crewItems = crew
        .map(HasThingIdOrSlug.parseIdOrSlug)
        .map(PersonCreditSearch(_, PersonAssociationType.Crew))

      Some(
        PeopleCreditSearch(
          castItems ++ crewItems,
          operator
        )
      )
    } else {
      None
    }
}
