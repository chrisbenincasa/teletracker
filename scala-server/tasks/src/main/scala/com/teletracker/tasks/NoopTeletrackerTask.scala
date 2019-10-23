package com.teletracker.tasks

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import io.circe.syntax._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class NoopTeletrackerTask extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = println(args)
}

class TimeoutTask extends TeletrackerTaskWithDefaultArgs {
  private val logger = LoggerFactory.getLogger(getClass)
  override def runInternal(args: Args): Unit = {
    val timeout = args.valueOrDefault("timeout", 1000)
    logger.info(s"Going to sleep for ${timeout}ms")
    Thread.sleep(timeout)
  }
}

class DependantTask @Inject()(publisher: SqsClient)
    extends TeletrackerTaskWithDefaultArgs {
  private val logger = LoggerFactory.getLogger(getClass)

  override def runInternal(args: Args): Unit = {
    val message =
      TeletrackerTaskQueueMessage(classOf[TimeoutTask].getName, Map(), None)

    logger.info(s"Publishing: $message")

    val response = publisher
      .sendMessage(
        SendMessageRequest
          .builder()
          .messageBody(message.asJson.noSpaces)
          .queueUrl(
            "https://sqs.us-west-1.amazonaws.com/302782651551/teletracker-tasks-qa"
          )
          .build()
      )

    logger.info(s"Published with id = ${response.messageId()}")
  }
}
