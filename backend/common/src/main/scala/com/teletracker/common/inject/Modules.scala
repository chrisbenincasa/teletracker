package com.teletracker.common.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.config.{ConfigLoader, TeletrackerConfig}
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
      new AwsModule
    )
  }
}

class ConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  @Provides
  @Singleton
  def config: TeletrackerConfig = {
    new ConfigLoader().load[TeletrackerConfig]("teletracker")
  }
}
