package com.teletracker.tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import io.circe.{Encoder, Json}
import io.circe.syntax._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class NoopTeletrackerTask extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = println(args)
}

class TimeoutTask extends TeletrackerTask {
  private val logger = LoggerFactory.getLogger(getClass)

  override type TypedArgs = Map[String, Json]

  implicit override protected def typedArgsEncoder: Encoder[Map[String, Json]] =
    io.circe.Encoder.encodeMap[String, Json]

  override def preparseArgs(args: Args): Map[String, Json] = {
    Map(
      "timeout" -> Json.fromInt(args.valueOrDefault("timeout", 1000))
    )
  }

  override def runInternal(args: Args): Unit = {
    val timeout = args.valueOrDefault("timeout", 1000)
    logger.info(s"Going to sleep for ${timeout}ms")
    Thread.sleep(timeout)
  }
}

class DependantTask @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val publisher: SqsClient)
    extends TeletrackerTaskWithDefaultArgs
    with SchedulesFollowupTasks {
  private val logger = LoggerFactory.getLogger(getClass)

  override def runInternal(args: Args): Unit = {
    logger.info("Running task and then going to schedule a follow-up")
  }

  override def followupTasksToSchedule(
    args: TypedArgs,
    rawArgs: Args
  ): List[TeletrackerTaskQueueMessage] = {
    List(TeletrackerTaskQueueMessage(classOf[TimeoutTask].getName, Map(), None))
  }
}
