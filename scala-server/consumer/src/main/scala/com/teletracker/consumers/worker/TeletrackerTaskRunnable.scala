package com.teletracker.consumers.worker

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.TeletrackerTask
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

class TeletrackerTaskRunnable(
  val originalMessage: TeletrackerTaskQueueMessage,
  teletrackerTask: TeletrackerTask,
  args: Map[String, Option[Any]])
    extends Runnable {

  private val callbacks = new ListBuffer[() => Unit]()

  override def run(): Unit = {
    try {
      teletrackerTask.runInternal(args)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      callbacks.foreach(_())
    }
  }

  def addCallback(cb: => Unit) = {
    synchronized {
      callbacks += (() => cb)
    }
  }
}
