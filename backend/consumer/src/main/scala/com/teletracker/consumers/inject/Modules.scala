package com.teletracker.consumers.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.config.ConfigLoader
import com.teletracker.common.inject.{Modules => CommonModules}
import com.teletracker.consumers.config.ConsumerConfig
import com.twitter.inject.TwitterModule
import scala.concurrent.ExecutionContext

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    CommonModules() ++ Seq(
      new ConsumerConfigModule()
    )
  }
}

class ConsumerConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  @Provides
  @Singleton
  def config: ConsumerConfig = {
    new ConfigLoader().load[ConsumerConfig]("teletracker.consumer")
  }
}
