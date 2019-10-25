package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.inject.Modules
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.consumers.impl.TaskQueueWorker
import com.teletracker.consumers.inject.HttpClientModule
import com.teletracker.consumers.worker.SqsQueueWorkerConfig
import com.teletracker.tasks.TeletrackerTaskRunner
import com.twitter.util.Await
import com.teletracker.common.util.Futures._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext.Implicits.global

object QueueConsumerDaemon extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new HttpClientModule)

  override protected def run(): Unit = {
    val config = injector.instance[TeletrackerConfig]

    val queue =
      new SqsQueue[TeletrackerTaskQueueMessage](
        SqsAsyncClient.create(),
        config.async.taskQueue.url
      )

    val worker = new TaskQueueWorker(
      queue,
      new SqsQueueWorkerConfig(batchSize = 1),
      injector.instance[TeletrackerTaskRunner]
    )

    val listener = new SqsQueueListener(
      worker
    )

    listener.start()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        // TODO: Requeue tasks
        queue.batchQueue(worker.getUnexecutedTasks.toList).await()
        listener.stop()
      }
    }))

    Await.ready(this)
  }
}