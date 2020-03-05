package com.teletracker.common.db.model

import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

object UserPreferences {
  val default = UserPreferences(
    presentationTypes = PresentationType.values().toSet,
    showOnlyNetworkSubscriptions = Some(false),
    hideAdultContent = Some(true)
  )
}

@JsonCodec
case class UserPreferences(
  presentationTypes: Set[PresentationType],
  showOnlyNetworkSubscriptions: Option[Boolean] = None,
  hideAdultContent: Option[Boolean] = None)
