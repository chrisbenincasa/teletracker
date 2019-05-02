package com.teletracker.service.inject

import com.teletracker.service.config.TeletrackerConfig
import com.google.inject.{Module, Provides, Singleton}
import com.twitter.inject.TwitterModule
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    Seq(
      new ConfigModule,
      new ExecutionContextModule,
      new DbModule
    )
  }
}

class ConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import com.teletracker.service.config.CustomReaders._

  @Provides
  @Singleton
  def config: TeletrackerConfig = {
    ConfigFactory.load().as[TeletrackerConfig]("teletracker")
  }
}