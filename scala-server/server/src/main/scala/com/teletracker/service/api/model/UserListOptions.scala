package com.teletracker.service.api.model

import com.teletracker.common.db.dynamo.model.UserListRowOptions
import com.teletracker.common.db.model.{DynamicListRules, TrackedListRowOptions}
import io.circe.Codec

case class UserListOptions(removeWatchedItems: Boolean) {
  def toRow: TrackedListRowOptions = TrackedListRowOptions(removeWatchedItems)
}

object UserListOptions {
  implicit val codec: Codec[UserListOptions] =
    io.circe.generic.semiauto.deriveCodec

  def fromRow(options: TrackedListRowOptions): UserListOptions = {
    UserListOptions(options.removeWatchedItems)
  }

  def fromRow(options: UserListRowOptions): UserListOptions = {
    UserListOptions(options.removeWatchedItems)
  }
}

case class UserListConfiguration(
  options: Option[UserListOptions],
  ruleConfiguration: Option[UserListRules])

object UserListConfiguration {
  implicit val codec: Codec[UserListConfiguration] =
    io.circe.generic.semiauto.deriveCodec

  def fromRow(
    rules: Option[DynamicListRules],
    options: Option[TrackedListRowOptions]
  ): UserListConfiguration = {
    UserListConfiguration(
      ruleConfiguration = rules.map(UserListRules.fromRow),
      options = options.map(UserListOptions.fromRow)
    )
  }

  def fromStoredConfiguration(
    rules: Option[DynamicListRules],
    options: Option[UserListRowOptions]
  ): UserListConfiguration = {
    UserListConfiguration(
      ruleConfiguration = rules.map(UserListRules.fromRow),
      options = options.map(UserListOptions.fromRow)
    )
  }
}
