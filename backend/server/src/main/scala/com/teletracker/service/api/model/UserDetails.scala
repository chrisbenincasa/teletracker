package com.teletracker.service.api.model

import com.teletracker.common.db.model.PresentationType
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class UserDetails(
  userId: String,
  preferences: UserPreferences,
  networkPreferences: List[Network])

@JsonCodec
case class UserPreferences(
  networkIds: Option[Set[Int]] = None,
  presentationTypes: Option[Set[PresentationType]] = None,
  showOnlyNetworkSubscriptions: Option[Boolean] = None,
  hideAdultContent: Option[Boolean] = None)
