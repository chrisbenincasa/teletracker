package com.teletracker.tasks.util

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.TeletrackerTaskRunner
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DirectTaskScheduler @Inject()(
  taskRunner: TeletrackerTaskRunner
)(implicit executionContext: ExecutionContext)
    extends TaskScheduler {
  override def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage,
    groupId: Option[String] = None
  ): Future[Unit] = {
    Future {
      taskRunner.runFromJsonArgs(
        teletrackerTaskQueueMessage.clazz,
        teletrackerTaskQueueMessage.args
      )
    }
  }

  override def schedule(
    teletrackerTaskQueueMessage: List[
      (TeletrackerTaskQueueMessage, Option[String])
    ]
  ): Future[Unit] = {
    AsyncStream
      .fromSeq(teletrackerTaskQueueMessage)
      .map(_._1)
      .mapF(message => {
        Future {
          taskRunner.runFromJsonArgs(message.clazz, message.args)
        }
      })
      .force
  }
}
