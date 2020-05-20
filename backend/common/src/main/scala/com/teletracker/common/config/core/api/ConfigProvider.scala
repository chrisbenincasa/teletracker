package com.teletracker.common.config.core.api

import scala.concurrent.duration.{Duration, _}

/**
  * A reloadable config can be reloaded at runtime
  *
  * @tparam T
  */
trait ReloadableConfig[+T] extends ConfigProvider[T] {
  self =>
  def map[U](f: T => U): ReloadableConfig[U] =
    new ProjectedReloadableConfig[T, U](this)(f)

  def watch(duration: Duration = 1 second)(onChange: T => Unit): WatchToken
}

/**
  * A generic configuration provider
  *
  * @tparam T
  */
trait ConfigProvider[+T] {
  def currentValue(): T
}
