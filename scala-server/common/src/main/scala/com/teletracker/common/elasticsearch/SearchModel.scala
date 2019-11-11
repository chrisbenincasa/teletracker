package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.PersonAssociationType
import com.teletracker.common.util.IdOrSlug

sealed trait BinaryOperator
object BinaryOperator {
  case object Or extends BinaryOperator
  case object And extends BinaryOperator
}

case class PersonCreditSearch(
  personId: IdOrSlug,
  associationType: PersonAssociationType)

case class PeopleCreditSearch(
  people: Seq[PersonCreditSearch],
  operator: BinaryOperator)
