package com.teletracker.tasks.config

import com.teletracker.common.config.core.api.ConfigWithPath

object TasksConfig extends ConfigWithPath {
  override type ConfigType = TasksConfig
  override val path: String = "teletracker.tasks"
}

case class TasksConfig(delta_thresholds: Map[String, Float])
