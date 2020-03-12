package com.teletracker.tasks.inject

import com.google.inject.Provides
import com.teletracker.common.aws.sqs.SqsTaskScheduler
import com.teletracker.common.pubsub.TaskScheduler
import com.teletracker.tasks.util.DirectTaskScheduler
import com.twitter.inject.TwitterModule
import com.twitter.inject.annotations.Flag

class TaskSchedulerModule extends TwitterModule {
  val scheduleMode =
    flag[String]("scheduleMode", "remote", "How to schedule tasks.")

  @Provides
  def provideTaskSchedule(
    @Flag("scheduleMode") scheduleMode: String,
    directTaskScheduler: DirectTaskScheduler,
    sqsTaskScheduler: SqsTaskScheduler
  ): TaskScheduler = {
    if (scheduleMode == "direct") {
      directTaskScheduler
    } else {
      sqsTaskScheduler
    }
  }
}
