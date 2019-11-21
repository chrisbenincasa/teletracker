package com.teletracker.common.config

import net.ceedubs.ficus.Ficus._
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.readers.ValueReader

class ConfigLoader {
  def load[A](path: String)(implicit valueReader: ValueReader[A]): A = {
    val resources = getResourceList(
      Option(System.getenv("ENV")).getOrElse("local")
    )
    val conf = resources
      .map(ConfigFactory.load)
      .reduceRight(_.withFallback(_))

    conf.as[A](path)
  }

  private def getResourceList(env: String) = Seq(
    s"$env.application.conf",
    "application.conf"
  )
}
