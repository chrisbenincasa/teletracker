package com.teletracker.consumers.worker

import org.slf4j.LoggerFactory
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
import scala.collection.JavaConverters._

class JobPool(
  name: String,
  size: Int) {
  private val logger = LoggerFactory.getLogger(getClass.getName + "#" + name)

  private[this] val pool = new ThreadPoolExecutor(
    size,
    size,
    0L,
    TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue[Runnable]
  )

  def submit(runnable: TeletrackerTaskRunnable): Unit = {
    runnable.addCallback {
      logger.info(s"Finished executing ${runnable.originalMessage.clazz}")
    }
    pool.submit(runnable)
    logger.info(s"Submitted ${runnable.originalMessage.clazz}")
  }

  def numOutstanding: Int = pool.getQueue.size()

  def getPending: Iterable[TeletrackerTaskRunnable] = {
    pool.getQueue.asScala.collect {
      case r: TeletrackerTaskRunnable => r
    }
  }
}
