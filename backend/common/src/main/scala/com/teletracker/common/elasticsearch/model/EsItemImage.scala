package com.teletracker.common.elasticsearch.model

import com.teletracker.common.elasticsearch.EsImageType
import io.circe.generic.JsonCodec

@JsonCodec
case class EsItemImage(
  provider_id: Int,
  provider_shortname: String,
  id: String,
  image_type: EsImageType)
