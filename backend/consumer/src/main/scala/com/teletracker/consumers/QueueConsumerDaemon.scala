package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.aws.sqs.SqsQueueListener
import com.teletracker.common.util.Futures._
import com.teletracker.consumers.impl.TaskQueueWorker
import com.teletracker.consumers.inject.{HttpClientModule, Modules}
import com.twitter.util.Await
import scala.concurrent.ExecutionContext.Implicits.global

object QueueConsumerDaemon extends com.twitter.inject.app.App {
  val mode = flag(
    "mode",
    "listen",
    "The mode to run the task consumer. Either \"listen\" or \"oneoff\""
  )

  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new HttpClientModule)

  override protected def run(): Unit = {
    val worker = injector.instance[TaskQueueWorker]

    val listener = new SqsQueueListener(
      worker
    )

    listener.start()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        worker.requeueUnfinishedTasks().await()
        listener.stop()
      }
    }))

    Await.ready(this)
  }
}
