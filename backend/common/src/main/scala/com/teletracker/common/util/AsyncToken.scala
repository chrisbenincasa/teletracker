package com.teletracker.common.util

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

trait AsyncToken[X, F[_]] {
  def awaitable: F[X]
  def await(duration: Duration = Duration.Inf): X
}

trait Cancellable {
  def cancel(): Unit
}

case class PromiseToken[X](awaitable: Promise[X])
    extends AsyncToken[X, Promise] {
  override def await(duration: Duration = Duration.Inf): X =
    Await.result(awaitable.future, duration)
}

object FutureToken {
  def successful[X](v: X): FutureToken[X] with Cancellable =
    new FutureToken(Future.successful(v)) with Cancellable {
      override def cancel(): Unit = {}
    }
}

case class FutureToken[X](awaitable: Future[X]) extends AsyncToken[X, Future] {
  override def await(duration: Duration = Duration.Inf): X =
    Await.result(awaitable, duration)
}
