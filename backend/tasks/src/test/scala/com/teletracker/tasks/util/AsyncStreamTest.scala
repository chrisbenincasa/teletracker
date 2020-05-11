package com.teletracker.tasks.util

import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import java.util.concurrent.Executors
import scala.concurrent.Future
import scala.concurrent.duration._

class AsyncStreamTest extends AnyFlatSpec {
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  it should "be distinct" in {
    println(
      AsyncStream.fromSeq(Seq(1, 2, 3, 3, 4, 4, 4)).distinct.toSeq().await()
    )
  }

  it should "mapDelayed" in {
    AsyncStream
      .fromSeq(1 to 5)
      .delayedMapF(1 second, scheduler)(i => {
        Future.successful(println(s"$i + ${System.currentTimeMillis()}"))
      })
  }
}
