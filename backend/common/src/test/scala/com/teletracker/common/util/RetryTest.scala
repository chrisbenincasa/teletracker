package com.teletracker.common.util

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Futures._
import com.teletracker.common.util.Retry.RetryOptions
import java.net.SocketTimeoutException
import scala.concurrent.duration._
import org.scalatest.flatspec.AnyFlatSpec

class RetryTest extends AnyFlatSpec {
  val scheduler = Executors.newSingleThreadScheduledExecutor()
  it should "retry a bit" in {
    val retries = new Retry(scheduler)

    val pf: PartialFunction[Throwable, Boolean] = {
      case e: SocketTimeoutException =>
        println("HEY")
        e.printStackTrace()
        true
    }

    var count = 0
    val options = RetryOptions(
      maxAttempts = 3,
      initialDuration = 5 seconds,
      maxDuration = 20 seconds,
      pf
    )
    val result = retries
      .withRetries(options)(() => {
        Future {
          println("Doing work")
          count += 1
          if (count < 3) {
            throw new IllegalArgumentException("bad")
          } else {
            "hey"
          }
        }
      })
      .await()

    println(result)
  }
}
