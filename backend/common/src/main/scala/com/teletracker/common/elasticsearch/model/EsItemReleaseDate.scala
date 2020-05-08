package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec
import java.time.LocalDate

@JsonCodec
case class EsItemReleaseDate(
  country_code: String,
  release_date: Option[LocalDate],
  certification: Option[String])
