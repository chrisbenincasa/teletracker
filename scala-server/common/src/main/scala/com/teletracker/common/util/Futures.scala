package com.teletracker.common.util

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Futures {
  implicit def richFuture[T](f: Future[T]): RichFuture[T] = new RichFuture[T](f)
}

class RichFuture[T](val f: Future[T]) extends AnyVal {
  def await(): T = Await.result(f, Duration.Inf)
}
