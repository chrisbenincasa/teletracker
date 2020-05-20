package com.teletracker.common.config.core.sources

import com.typesafe.config.Config

case class AppName(value: String) extends AnyVal

trait Source {
  def contents(): Option[String]

  def appName: AppName

  def description: String
}

trait SourceFactory {
  def source(
    config: Config,
    appName: AppName
  ): Source
}
