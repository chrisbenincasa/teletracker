package com.teletracker.tasks

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessageFactory
}
import com.teletracker.common.tasks.{
  TypedTeletrackerTask,
  UntypedTeletrackerTask
}
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.annotations.TaskTags
import io.circe.generic.JsonCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.util.control.NonFatal

@JsonCodec
@GenArgParser
case class RemoteTaskArgs(
  classToRun: String,
  instances: Int = 1)

object RemoteTaskArgs

class RemoteTask @Inject()(
  teletrackerTaskRunner: TeletrackerTaskRunner,
  taskScheduler: TaskScheduler)
    extends TypedTeletrackerTask[RemoteTaskArgs] {
  override def runInternal(): Unit = {
    val instance =
      teletrackerTaskRunner.getInstance(args.classToRun)

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
        args.classToRun,
        jsonArgs,
        Some(tags)
      )

    println(message.asJson)

    (0 until args.instances).foreach(_ => {
      taskScheduler.schedule(message).await()
    })
  }
}
