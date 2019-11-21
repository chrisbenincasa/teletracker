package com.teletracker.consumers.config

import com.teletracker.common.config.ConfigLoader
import org.scalatest.FlatSpec

class ConfigLoadTest extends FlatSpec {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import com.teletracker.common.config.CustomReaders._

  it should "load" in {
    println(new ConfigLoader().load[ConsumerConfig]("teletracker.consumer"))
  }
}
