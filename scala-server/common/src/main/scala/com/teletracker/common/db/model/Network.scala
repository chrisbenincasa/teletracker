package com.teletracker.common.db.model

import com.teletracker.common.util.Slug

case class Network(
  id: Option[Int],
  name: String,
  slug: Slug,
  shortname: String,
  homepage: Option[String],
  origin: Option[String])
