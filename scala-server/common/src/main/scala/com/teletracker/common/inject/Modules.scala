package com.teletracker.common.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.config.{ConfigLoader, TeletrackerConfig}
import com.twitter.inject.TwitterModule
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    Seq(
      new ConfigModule,
      new ExecutionContextModule,
      new BaseDbModule,
      new DbModule,
      new BackgroundProcessorModule,
      new CacheModule,
//      new GoogleModule,
      new OpenCensusMetricsModule,
      new CodahaleMetricsModule,
      new ElasticsearchModule,
      new AwsModule
    )
  }
}

class ConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import com.teletracker.common.config.CustomReaders._

  @Provides
  @Singleton
  def config: TeletrackerConfig = {
    new ConfigLoader().load[TeletrackerConfig]("teletracker")
  }
}
