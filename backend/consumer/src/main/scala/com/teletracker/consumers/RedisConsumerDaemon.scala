package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.aws.sqs.SqsQueueListener
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.Ack
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueBatchWorker,
  SqsQueueWorkerConfig
}
import com.teletracker.common.config.core.api.StaticConfig
import com.teletracker.common.model.scraping.google.GooglePlayStoreItem
import com.teletracker.common.pubsub.TransparentEventBase
import com.teletracker.consumers.inject.Modules
import com.teletracker.consumers.redis.RedisListQueue
import com.twitter.finagle.redis.Client
import com.twitter.util.Await
import scala.concurrent.ExecutionContext.Implicits.global

object RedisConsumerDaemon extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] =
    Modules()

  override protected def run(): Unit = {
    val worker =
      SqsQueueBatchWorker[TransparentEventBase[GooglePlayStoreItem]](
        new RedisListQueue[TransparentEventBase[GooglePlayStoreItem]](
          "google_play_store_distributed:items",
          Client("localhost:6379")
        ) {},
        StaticConfig(new SqsQueueWorkerConfig())
      )(items => {
        items.map(item => {
          println(item)
          Ack(item.receiptHandle.get)
        })
      })

    val listener =
      new SqsQueueListener[TransparentEventBase[GooglePlayStoreItem]](worker)
    listener.start()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        listener.stop()
      }
    }))

    Await.ready(this)
  }
}
