package com.teletracker.common.db.model

// Backcomapt for supported networks to DB networks
object SupportedNetworkLookup {
  final val SpecialCaseMap =
    Map(
      "apple-itunes" -> SupportedNetwork.AppleTv
    )
}
