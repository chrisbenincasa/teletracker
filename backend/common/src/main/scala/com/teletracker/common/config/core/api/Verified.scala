package com.teletracker.common.config.core.api

/**
  * A configuration companion object can be marked as Verified and will be executed
  *
  * before the config is loaded. This lets configurations define system level pre-checks before they can be loaded
  *
  * If the config object implements this itself it will be called _post_ load, and on subsequent re-loads.
  */
trait Verified {
  def verify(): Unit
}

/**
  * Throw subtypes of this in post verification otherwise a generic exception will bubble up
  */
trait InvalidConfigurationException {
  self: Exception =>
}
