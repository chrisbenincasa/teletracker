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

  private val callbacks = new ListBuffer[(Option[Throwable]) => Unit]()

  override def run(): Unit = {
    try {
      teletrackerTask.run(args)
      callbacks.foreach(_(None))
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        callbacks.foreach(_(Some(e)))
    }
  }

  def addCallback(cb: Option[Throwable] => Unit) = {
    synchronized {
      callbacks += cb
    }
  }
}
