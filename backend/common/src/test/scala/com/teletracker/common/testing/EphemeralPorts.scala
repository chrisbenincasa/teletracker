package com.teletracker.common.testing

import java.util.concurrent.ConcurrentHashMap
import scala.util.Random

object EphemeralPorts {
  private val used = ConcurrentHashMap.newKeySet[Int]()

  def get: Int = used.synchronized {
    val port = Stream
      .continually(32768 + Random.nextInt(60999 - 32768))
      .dropWhile(used.contains)
      .head
    used.add(port)
    port
  }
}
