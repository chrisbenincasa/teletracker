package com.chrisbenincasa.services.teletracker.util.execution

import com.twitter.finagle.param.HighResTimer
import com.twitter.util.Duration
import scala.collection.generic.CanBuildFrom
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

object SequentialFutures {
  def serialize[Element, OtherElement, Collection[Element] <: Iterable[Element]](
    collection: Collection[Element],
    perElementWait: Option[FiniteDuration] = None
  )(fn: Element => Future[OtherElement])(
    implicit executionContext: ExecutionContext,
    cbf: CanBuildFrom[Collection[OtherElement], OtherElement, Collection[OtherElement]]
  ): Future[Collection[OtherElement]] = {
    val builder = cbf()
    builder.sizeHint(collection.size)

    collection.foldLeft(Future(builder)) {
      (previousFuture, next) =>
        for {
          previousResults <- previousFuture
          _ <- perElementWait.map(makeWaitFuture).getOrElse(Future.unit)
          next <- fn(next)
        } yield previousResults += next
    } map { builder => builder.result }
  }

  private def makeWaitFuture(wait: FiniteDuration): Future[Unit] = {
    val p = Promise[Unit]()
    HighResTimer.Default.doLater(Duration(wait.length, wait.unit)) {
      p.success(())
    }
    p.future
  }
}
