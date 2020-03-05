package com.teletracker.common.db.model

import java.util.UUID

case class PersonThing(
  personId: UUID,
  thingId: UUID,
  relationType: PersonAssociationType,
  characterName: Option[String],
  order: Option[Int],
  department: Option[String],
  job: Option[String])
