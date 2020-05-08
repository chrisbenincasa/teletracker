package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec

@JsonCodec
case class EsItemVideo(
  provider_id: Int,
  provider_shortname: String,
  provider_source_id: String,
  name: Option[String],
  language_code: Option[String],
  country_code: Option[String],
  video_source: String,
  video_source_id: String,
  size: Option[Int],
  video_type: Option[String])
