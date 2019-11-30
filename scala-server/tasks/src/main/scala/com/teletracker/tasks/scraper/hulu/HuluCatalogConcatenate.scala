package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.util.TaskMessageHelper
import com.teletracker.tasks.{
  SchedulesFollowupTasks,
  TeletrackerTaskWithDefaultArgs
}
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsClient

class HuluCatalogConcatenate @Inject()(protected val publisher: SqsClient)
    extends TeletrackerTaskWithDefaultArgs
    with SchedulesFollowupTasks {
  override def followupTasksToSchedule(
    args: TypedArgs,
    rawArgs: Args
  ): List[TeletrackerTaskQueueMessage] = {
    val scheduleIngestJob = rawArgs.valueOrDefault("scheduleIngestJob", false)

    if (scheduleIngestJob) {
      List(
        TaskMessageHelper.forTask[HuluDeltaLocator](Map())
      )
    } else {
      Nil
    }
  }

  override protected def runInternal(args: Args): Unit = ???
}
