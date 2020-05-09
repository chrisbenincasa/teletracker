package com.teletracker.tasks.util

import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec

class AsyncStreamTest extends AnyFlatSpec {
  it should "be distinct" in {
    println(
      AsyncStream.fromSeq(Seq(1, 2, 3, 3, 4, 4, 4)).distinct.toSeq().await()
    )
  }
}
