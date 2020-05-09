package com.teletracker.common.execution

import com.teletracker.common.util.Futures._
import com.teletracker.common.util.execution.SequentialFutures
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.flatspec.AnyFlatSpec

class SequentialFuturesTest extends AnyFlatSpec {
  private val logger = LoggerFactory.getLogger(getClass)
  it should "do" in {
    val it = Stream.range(0, 100).iterator
    SequentialFutures
      .batchedIterator(it, 4, Some(1000 millis))(x => {
        Future.successful(logger.info(s"${x.mkString(", ")}"))
      })
      .await()
  }
}
