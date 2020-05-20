package com.teletracker.common.config.core.api

import scala.concurrent.duration.Duration

/**
  * A static config that does not change
  *
  * @param data
  * @tparam T
  */
case class StaticConfig[T](data: T) extends ReloadableConfig[T] {
  override def watch(duration: Duration)(onChange: (T) => Unit): WatchToken = {
    new WatchToken(None)
  }

  override def currentValue(): T = data
}
