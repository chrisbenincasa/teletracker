package com.teletracker.service.api.model

import com.teletracker.common.db.model.{Network, UserPreferences}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class UserDetails(
  userId: String,
  preferences: UserPreferences,
  networkPreferences: List[Network])
