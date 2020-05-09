package com.teletracker.consumers.config

import com.teletracker.common.config.ConfigLoader
import org.scalatest.flatspec.AnyFlatSpec

class ConfigLoadTest extends AnyFlatSpec {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  it should "load" in {
    println(new ConfigLoader().load[ConsumerConfig]("teletracker.consumer"))
  }
}
