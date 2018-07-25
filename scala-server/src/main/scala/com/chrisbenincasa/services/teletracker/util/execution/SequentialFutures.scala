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

    collection.foldLeft(Future.successful(builder)) {
      (previousFuture, next) =>
        for {
          previousResults <- previousFuture
          _ <- perElementWait.map(makeWaitFuture).getOrElse(Future.unit)
          next <- fn(next)
        } yield previousResults += next
    } map { builder => builder.result }
  }

  def batchedIterator[Element, OtherElement](
    collection: Iterator[Element],
    batchSize: Int,
    aggregator: (List[OtherElement], List[OtherElement]) => List[OtherElement] = (a: List[OtherElement], b: List[OtherElement]) => a ++ b
  )(fn: Iterable[Element] => Future[Iterable[OtherElement]])(
    implicit ec: ExecutionContext
  ): Future[List[OtherElement]] = {
    def process(curr: List[Element], acc: List[OtherElement]): Future[List[OtherElement]] = {
      fn(curr).flatMap(result => {
        val nextAcc = aggregator(result.toList, acc)

        if (collection.isEmpty) {
          Future.successful(nextAcc)
        } else {
          process(collection.take(batchSize).toList, nextAcc)
        }
      })
    }

    process(collection.take(batchSize).toList, Nil)
  }

  private def makeWaitFuture(wait: FiniteDuration): Future[Unit] = {
    val p = Promise[Unit]()
    HighResTimer.Default.doLater(Duration(wait.length, wait.unit)) {
      p.success(())
    }
    p.future
  }
}
