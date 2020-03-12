package com.teletracker.tasks.util

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.tasks.TeletrackerTaskRunner
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DirectTaskScheduler @Inject()(
  taskRunner: TeletrackerTaskRunner
)(implicit executionContext: ExecutionContext)
    extends TaskScheduler {
  override def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage
  ): Future[Unit] = {
    Future {
      taskRunner.runFromJson(
        teletrackerTaskQueueMessage.clazz,
        teletrackerTaskQueueMessage.args
      )
    }
  }
}
