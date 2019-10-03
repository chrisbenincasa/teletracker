package com.teletracker.consumers

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.{DependantTask, TimeoutTask}
import io.circe.Json
import io.circe.syntax._

object EnqueueTest extends App {
//  val publisher = Publisher.newBuilder("teletracker-task-queue").build()

  val message = TeletrackerTaskQueueMessage(
    classOf[DependantTask].getName,
    Map(
      )
  )

  println(message.asJson.spaces4)
}
