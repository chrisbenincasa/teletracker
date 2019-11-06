package com.teletracker.consumers.worker

import org.slf4j.LoggerFactory
import java.util.concurrent.{
  LinkedBlockingQueue,
  RejectedExecutionException,
  ThreadPoolExecutor,
  TimeUnit
}
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
    new LinkedBlockingQueue[Runnable](size)
  )

  def submit(runnable: TeletrackerTaskRunnable): Boolean = {
    runnable.addCallback { _ =>
      logger.info(s"Finished executing ${runnable.originalMessage.clazz}")
    }
    try {
      pool.submit(runnable)
      logger.info(s"Submitted ${runnable.originalMessage.clazz}")
      true
    } catch {
      case _: RejectedExecutionException =>
        logger.info(
          s"Submission of ${runnable.originalMessage.clazz} rejected. "
        )
        false
    }
  }

  def numOutstanding: Int = pool.getQueue.size()

  def getPending: Iterable[TeletrackerTaskRunnable] = {
    pool.getQueue.asScala.collect {
      case r: TeletrackerTaskRunnable => r
    }
  }
}
