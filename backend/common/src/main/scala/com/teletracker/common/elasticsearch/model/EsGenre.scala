package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec

@JsonCodec
case class EsGenre(
  id: Int,
  name: String)
