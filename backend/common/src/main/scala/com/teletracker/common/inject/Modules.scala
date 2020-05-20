package com.teletracker.common.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.config.core.ConfigLoader
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.config.core.sources.AppName
import com.twitter.inject.TwitterModule
import scala.concurrent.ExecutionContext

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    Seq(
      new ConfigModule,
      new ExecutionContextModule,
      new CacheModule,
      new CodahaleMetricsModule,
      new ElasticsearchModule,
      new AwsModule,
      new QueuesModule
    )
  }
}

class ConfigModule extends TwitterModule {
  import com.teletracker.common.config.core.readers.ValueReaders._

  @Provides
  @Singleton
  def configLoader(implicit executionContext: ExecutionContext): ConfigLoader =
    new ConfigLoader(AppName("teletracker"))

  @Provides
  @Singleton
  def config(
    configLoader: ConfigLoader
  ): ReloadableConfig[TeletrackerConfig] = {
    configLoader.loadType(TeletrackerConfig)
  }

  @Provides
  @Singleton
  def staticConfig(
    reloadable: ReloadableConfig[TeletrackerConfig]
  ): TeletrackerConfig =
    reloadable.currentValue()
}
