package com.teletracker.common.util

import com.teletracker.common.util.execution.SequentialFutures
import java.util.concurrent.CompletableFuture
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

object Futures {
  implicit def richFuture[T](f: Future[T]): RichFuture[T] = new RichFuture[T](f)

  implicit def richFutureCollection[T, Collection[T] <: Traversable[T]](
    l: Collection[T]
  ): RichFutureTraversable[T, Collection] =
    new RichFutureTraversable[T, Collection](l)
}

class RichFuture[T](val f: Future[T]) extends AnyVal {
  def await(): T = Await.result(f, Duration.Inf)

  def through[U](
    pf: PartialFunction[Try[T], Future[U]]
  )(implicit executor: ExecutionContext
  ): Future[T] =
    f.transformWith { result =>
      pf.applyOrElse[Try[T], Future[Any]](result, x => Future.successful(x))
        .transform(_ => result)
        .recoverWith {
          case NonFatal(t) => Future.failed(t)
        }
    }
}

class RichFutureTraversable[T, Coll[T] <: Traversable[T]](val l: Coll[T])
    extends AnyVal {
  def sequentially[U](
    f: T => Future[U]
  )(implicit executionContext: ExecutionContext,
    cbf: CanBuildFrom[Coll[U], U, Coll[U]]
  ): Future[Coll[U]] = {
    SequentialFutures.serialize[T, U, Coll](l)(f)
  }
}
