package com.teletracker.tasks

import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import javax.inject.Inject
import io.circe.syntax._
import org.slf4j.LoggerFactory

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

class DependantTask @Inject()(publisher: Publisher)
    extends TeletrackerTaskWithDefaultArgs {
  private val logger = LoggerFactory.getLogger(getClass)

  override def runInternal(args: Args): Unit = {
    val message =
      TeletrackerTaskQueueMessage(classOf[TimeoutTask].getName, Map(), None)

    logger.info(s"Publishing: $message")

    val messageId = publisher
      .publish(
        PubsubMessage
          .newBuilder()
          .setData(ByteString.copyFrom(message.asJson.noSpaces.getBytes()))
          .build()
      )
      .get()

    logger.info(s"Published with id = $messageId")

    publisher.publishAllOutstanding()
  }
}
