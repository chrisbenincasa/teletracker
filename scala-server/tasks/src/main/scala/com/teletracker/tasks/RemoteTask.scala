package com.teletracker.tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessageFactory
import com.teletracker.tasks.annotations.TaskTags
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import scala.util.control.NonFatal

class RemoteTask @Inject()(
  publisher: SqsClient,
  teletrackerTaskRunner: TeletrackerTaskRunner,
  teletrackerConfig: TeletrackerConfig)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val clazz = args.value[String]("classToRun").get
    val instance =
      teletrackerTaskRunner.getInstance(clazz)

    val jsonArgs = instance.argsAsJson(args)

    val tags = try {
      Class
        .forName(
          clazz
        )
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

    publisher
      .sendMessage(
        SendMessageRequest
          .builder()
          .messageBody(message.asJson.noSpaces)
          .queueUrl(
            teletrackerConfig.async.taskQueue.url
          )
          .build()
      )
  }
}
