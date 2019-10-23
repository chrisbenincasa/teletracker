package com.teletracker.tasks

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessageFactory
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class RemoteTask @Inject()(
  publisher: SqsClient,
  teletrackerTaskRunner: TeletrackerTaskRunner)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val clazz = args.value[String]("classToRun").get
    val instance =
      teletrackerTaskRunner.getInstance(clazz)

    val jsonArgs = instance.argsAsJson(args)

    val message =
      TeletrackerTaskQueueMessageFactory.withJsonArgs(clazz, jsonArgs, None)
    println(message.asJson)

    publisher
      .sendMessage(
        SendMessageRequest
          .builder()
          .messageBody(message.asJson.noSpaces)
          .queueUrl(
            "https://sqs.us-west-1.amazonaws.com/302782651551/teletracker-tasks-qa"
          )
          .build()
      )
  }
}
