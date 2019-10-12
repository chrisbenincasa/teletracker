package com.teletracker.common.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.config.TeletrackerConfig
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
      new GoogleModule,
      new OpenCensusMetricsModule,
      new CodahaleMetricsModule
    )
  }
}

class ConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import com.teletracker.common.config.CustomReaders._

  private def getResourceList(env: String) = Seq(
    s"$env.application.conf",
    "application.conf"
  )

  @Provides
  @Singleton
  def config: TeletrackerConfig = {
    val env = System.getenv()
    val resources = getResourceList(
      Option(System.getenv("ENV")).getOrElse("local")
    )
    val conf = resources
      .map(ConfigFactory.load)
      .reduceRight(_.withFallback(_))
    conf.as[TeletrackerConfig]("teletracker")
  }
}