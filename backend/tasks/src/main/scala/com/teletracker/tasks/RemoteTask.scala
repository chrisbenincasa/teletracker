package com.teletracker.tasks

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessageFactory
}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.annotations.TaskTags
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.util.UUID
import scala.util.control.NonFatal
import scala.compat.java8.FutureConverters._

class RemoteTask @Inject()(
  publisher: SqsAsyncClient,
  teletrackerTaskRunner: TeletrackerTaskRunner,
  teletrackerConfig: TeletrackerConfig,
  taskScheduler: TaskScheduler)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val clazz = args.value[String]("classToRun").get
    val instances = args.valueOrDefault[Int]("instances", 1)
    val instance =
      teletrackerTaskRunner.getInstance(clazz)

    val jsonArgs = instance.argsAsJson(args)

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
