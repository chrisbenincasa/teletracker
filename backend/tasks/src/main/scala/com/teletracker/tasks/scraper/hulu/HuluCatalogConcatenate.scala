package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.{TaskMessageHelper, TeletrackerTask}
import com.teletracker.tasks.general.ConcatenateTask
import com.teletracker.tasks.scraper
import com.teletracker.tasks.util.Concatenator
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient

class HuluCatalogConcatenate @Inject()(
  concatenator: Concatenator,
  protected val publisher: SqsAsyncClient)
    extends ConcatenateTask(concatenator) {

  override def preparseArgs(args: RawArgs): ArgsType = {
    super.preparseArgs(args) ++ Map(
      "scheduleIngestJob" -> args
        .valueOrDefault("scheduleIngestJob", "false")
    )
  }

  override def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] = {
    val scheduleIngestJob = rawArgs.valueOrDefault("scheduleIngestJob", false)

    if (scheduleIngestJob) {
      List(
        TeletrackerTask.taskMessage[LocateAndRunHuluCatalogDelta](
          scraper.DeltaLocatorJobArgs(maxDaysBack = 3, local = false)
        )
      )
    } else {
      Nil
    }
  }
}
