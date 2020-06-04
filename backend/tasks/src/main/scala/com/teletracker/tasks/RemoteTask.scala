package com.teletracker.tasks

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessageFactory
}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.annotations.TaskTags
import io.circe.syntax._
import javax.inject.Inject
import scala.util.control.NonFatal

class RemoteTask @Inject()(
  teletrackerTaskRunner: TeletrackerTaskRunner,
  taskScheduler: TaskScheduler)
    extends UntypedTeletrackerTask {
  override def runInternal(): Unit = {
    val clazz = rawArgs.value[String]("classToRun").get
    val instances = rawArgs.valueOrDefault[Int]("instances", 1)
    val instance =
      teletrackerTaskRunner.getInstance(clazz)

    val jsonArgs = instance.argsAsJson(rawArgs - "classToRun" - "instances")

    val tags = try {
      instance.getClass
        .getAnnotation(classOf[TaskTags])
        .tags()
        .toSet
    } catch {
      case NonFatal(e: NullPointerException) => Set.empty[String]
    }

    val message =
      TeletrackerTaskQueueMessageFactory.withJsonArgs(
        clazz,
        jsonArgs,
        Some(tags)
      )

    println(message.asJson)

    (0 until instances).foreach(_ => {
      taskScheduler.schedule(message).await()
    })
  }
}
