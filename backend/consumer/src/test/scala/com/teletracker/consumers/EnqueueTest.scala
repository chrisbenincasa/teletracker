package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.inject.Modules
import com.teletracker.tasks.{DependantTask, TeletrackerTaskRunner}
import scala.concurrent.ExecutionContext.Implicits.global

object EnqueueTest extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {

    val config = injector.instance[TeletrackerConfig]

//    val queue =
//      new SqsQueue[TeletrackerTaskQueueMessage](
//        SqsAsyncClient.create(),
//        config.async.taskQueue.url
//      )
//
//    val message = TeletrackerTaskQueueMessage(
//      classOf[DependantTask].getName,
//      Map(
//        )
//    )

    val instance = injector
      .instance[TeletrackerTaskRunner]
      .getInstance(classOf[DependantTask].getName)
    instance.run(Map())

//    queue.queue(message).await()
//
//    println(message.asJson.spaces4)
  }
}
