package com.teletracker.common.config.core.api

/**
  * An object defining the type of a config and the path it can be found at
  */
trait ConfigWithPath {
  type ConfigType

  val path: String
}
