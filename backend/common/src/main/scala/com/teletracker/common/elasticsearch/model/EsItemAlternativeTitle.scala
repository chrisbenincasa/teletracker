package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec

@JsonCodec
case class EsItemAlternativeTitle(
  country_code: String,
  title: String,
  `type`: Option[String])
