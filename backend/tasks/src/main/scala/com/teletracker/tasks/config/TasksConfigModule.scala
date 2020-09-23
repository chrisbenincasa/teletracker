package com.teletracker.tasks.config

import com.google.inject.{Provides, Singleton}
import com.teletracker.common.config.core.ConfigLoader
import com.teletracker.common.config.core.api.ReloadableConfig
import com.twitter.inject.TwitterModule

class TasksConfigModule extends TwitterModule {
  import com.teletracker.common.config.core.readers.ValueReaders._

  @Provides
  @Singleton
  def config(configLoader: ConfigLoader): ReloadableConfig[TasksConfig] = {
    configLoader.loadType(TasksConfig)
  }
}
