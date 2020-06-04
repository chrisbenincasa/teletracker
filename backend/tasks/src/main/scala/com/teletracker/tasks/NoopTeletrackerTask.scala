package com.teletracker.tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.{TeletrackerTask, UntypedTeletrackerTask}
import io.circe.Json
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID

class NoopTeletrackerTask extends UntypedTeletrackerTask {
  override def runInternal(): Unit = println(args)
}

class TimeoutTask extends TeletrackerTask {
  override type ArgsType = Map[String, Json]

  override def argsAsJson(args: RawArgs): Json =
    io.circe.Encoder.encodeMap[String, Json].apply(preparseArgs(args))

  override def preparseArgs(args: RawArgs): Map[String, Json] = {
    Map(
      "timeout" -> Json.fromInt(args.valueOrDefault("timeout", 1000))
    )
  }

  override def runInternal(): Unit = {
    val timeout = rawArgs.valueOrDefault("timeout", 1000)
    logger.info(s"Going to sleep for ${timeout}ms")
    Thread.sleep(timeout)
  }
}

class DependantTask @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val publisher: SqsAsyncClient)
    extends UntypedTeletrackerTask {

  override def runInternal(): Unit = {
    logger.info("Running task and then going to schedule a follow-up")
  }

  override def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] = {
    List(
      TeletrackerTaskQueueMessage(
        Some(UUID.randomUUID()),
        classOf[TimeoutTask].getName,
        Map(),
        None
      )
    )
  }
}
