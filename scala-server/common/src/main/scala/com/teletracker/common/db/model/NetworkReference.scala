package com.teletracker.common.db.model

case class NetworkReference(
  id: Option[Int],
  externalSource: ExternalSource,
  externalId: String,
  networkId: Int)
