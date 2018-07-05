package com.chrisbenincasa.services.teletracker.util.execution

import scala.collection.generic.CanBuildFrom
import scala.concurrent.{ExecutionContext, Future}

object SequentialFutures {
  def serialize[Element, OtherElement, Collection[Element] <: Iterable[Element]](
    collection: Collection[Element]
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
          next <- fn(next)
        } yield previousResults += next
    } map { builder => builder.result }
  }
}
