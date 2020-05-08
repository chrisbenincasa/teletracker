package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.util.UUID

@JsonCodec
case class EsPersonCrewCredit(
  id: UUID,
  title: String,
  department: Option[String],
  job: Option[String],
  `type`: ItemType,
  slug: Option[Slug])
