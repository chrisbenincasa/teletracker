package com.teletracker.consumers

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.twitter.concurrent.NamedPoolThreadFactory
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{
  ConcurrentHashMap,
  ConcurrentLinkedQueue,
  Executors,
  RejectedExecutionException
}
import java.util.function.Supplier
import scala.collection.JavaConverters._

class JobPool(
  name: String,
  size: Supplier[Int]) {
  private val logger = LoggerFactory.getLogger(getClass.getName + "#" + name)

  private[this] val currentlyRunning =
    new ConcurrentHashMap[UUID, TeletrackerTaskRunnable]()

  private[this] val pool =
    Executors.newCachedThreadPool(new NamedPoolThreadFactory(name))

  def submit(runnable: TeletrackerTaskRunnable): Boolean = synchronized {
    if (currentlyRunning.size() < size.get()) {
      runnable.addCallback { (_, _) =>
        logger.info(s"Finished executing ${runnable.originalMessage.clazz}")
        currentlyRunning.remove(runnable.id)
      }

      try {
        pool.submit(runnable)
        currentlyRunning.put(runnable.id, runnable)
        logger.info(s"Submitted ${runnable.originalMessage.clazz}")
        true
      } catch {
        case _: RejectedExecutionException =>
          logger.warn(
            s"Submission of ${runnable.originalMessage.clazz} rejected. "
          )
          false
      }
    } else {
      logger.info(
        s"Cannot submit new job. Max number of jobs active: ${size.get()}"
      )
      false
    }
  }

  def numOutstanding: Int = currentlyRunning.size()

  def getPending: Iterable[TeletrackerTaskRunnable] = {
    currentlyRunning.values().asScala
  }
}
