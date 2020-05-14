package com.teletracker.consumers

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import org.slf4j.MDC
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

class TeletrackerTaskRunnable(
  val originalMessage: TeletrackerTaskQueueMessage,
  teletrackerTask: TeletrackerTask,
  args: Map[String, Option[Any]])
    extends Runnable {

  private val callbacks =
    new ListBuffer[(TeletrackerTask, TeletrackerTask.TaskResult) => Unit]()

  override def run(): Unit = {
    MDC.put("task", teletrackerTask.getClass.getName)
    MDC.put("taskId", teletrackerTask.taskId.toString)
    val result = try {
      teletrackerTask.run(args)
    } catch {
      case NonFatal(e) =>
        System.err.println(
          s"Uncaught exception from task: ${teletrackerTask.getClass.getName}"
        )
        e.printStackTrace()
        TeletrackerTask.TaskResult.failure(e)
    }

    MDC.clear()
    callbacks.foreach(_(teletrackerTask, result))
  }

  def addCallback(cb: (TeletrackerTask, TeletrackerTask.TaskResult) => Unit) = {
    synchronized {
      callbacks += cb
    }
  }
}
