package com.teletracker.common.api.model

import io.circe.generic.JsonCodec

@JsonCodec
case class TrackedListRowOptions(removeWatchedItems: Boolean)
