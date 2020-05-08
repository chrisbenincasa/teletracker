package com.teletracker.tasks

import com.teletracker.common.tasks.{
  TeletrackerTask,
  TeletrackerTaskWithDefaultArgs
}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import io.circe.{Encoder, Json}
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID

class NoopTeletrackerTask extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = println(args)
}

class TimeoutTask extends TeletrackerTask {
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
  protected val publisher: SqsAsyncClient)
    extends TeletrackerTaskWithDefaultArgs {

  override def runInternal(args: Args): Unit = {
    logger.info("Running task and then going to schedule a follow-up")
  }

  override def followupTasksToSchedule(
    args: TypedArgs,
    rawArgs: Args
  ): List[TeletrackerTaskQueueMessage] = {
    List(
      TeletrackerTaskQueueMessage(
        UUID.randomUUID(),
        classOf[TimeoutTask].getName,
        Map(),
        None
      )
    )
  }
}
