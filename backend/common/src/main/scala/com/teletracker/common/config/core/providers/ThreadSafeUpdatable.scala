package com.teletracker.common.config.core.providers

import scala.collection.mutable

trait ThreadSafeSubscriber[T] {
  def onChange(data: T): Unit
}

/**
  * Thread safe box
  *
  * @param source
  * @tparam T
  */
class ThreadSafeUpdatable[T](private var source: T) {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private val callbacks = new mutable.ArrayBuffer[ThreadSafeSubscriber[T]]()

  def currentValue(): T = {
    synchronized {
      source
    }
  }

  def update(newData: T): Unit = {
    synchronized {
      source = newData

      callbacks.foreach(
        x =>
          try {
            x.onChange(newData)
          } catch {
            case ex: Throwable =>
              logger.warn("Unable to update subscriber!", ex)
          }
      )
    }
  }

  def register(threadSafeSubscriber: ThreadSafeSubscriber[T]): Unit = {
    synchronized {
      callbacks += threadSafeSubscriber
    }
  }

  def unregister(threadSafeSubscriber: ThreadSafeSubscriber[T]): Unit = {
    synchronized {
      callbacks -= threadSafeSubscriber
    }
  }
}
