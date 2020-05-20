package com.teletracker.common.config.core.scheduler

import java.util.concurrent.ThreadFactory
import scala.util.Random

class DaemonThreadFactory(name: String) extends ThreadFactory {
  private val rand = new Random()
  override def newThread(r: Runnable): Thread = {
    val t = new Thread(r)
    t.setDaemon(true)
    t.setName(name + rand.nextInt())
    t
  }
}
