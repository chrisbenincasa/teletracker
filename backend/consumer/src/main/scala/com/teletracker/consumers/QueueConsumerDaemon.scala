package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.aws.sqs.worker.SqsQueueThroughputWorkerConfig
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.aws.sqs.{SqsQueue, SqsQueueListener}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.util.Futures._
import com.teletracker.consumers.config.ConsumerConfig
import com.teletracker.consumers.impl.TaskQueueWorker
import com.teletracker.consumers.inject.{HttpClientModule, Modules}
import com.teletracker.tasks.TeletrackerTaskRunner
import com.twitter.util.Await
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object QueueConsumerDaemon extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new HttpClientModule)

  override protected def run(): Unit = {
    val config = injector.instance[TeletrackerConfig]

    val sqsClient = injector.instance[SqsAsyncClient]
    val queue =
      new SqsQueue[TeletrackerTaskQueueMessage](
        sqsClient,
        config.async.taskQueue.url
      )
    val dlq = config.async.taskQueue.dlq.map(dlqConf => {
      new SqsQueue[TeletrackerTaskQueueMessage](sqsClient, dlqConf.url)
    })

    val worker = new TaskQueueWorker(
      queue,
      dlq,
      new SqsQueueThroughputWorkerConfig(
        maxOutstandingItems = 2,
        heartbeat = Some(
          HeartbeatConfig(
            heartbeat_frequency = 15 seconds,
            visibility_timeout = 5 minutes
          )
        )
      ),
      injector.instance[TeletrackerTaskRunner],
      injector.instance[ConsumerConfig]
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
