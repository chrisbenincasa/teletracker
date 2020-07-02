package com.teletracker.tasks.model

import scala.concurrent.duration.FiniteDuration

trait BaseTaskArgs {
  def dryRun: Boolean

  def sleepBetweenWriteMs: Option[Long]
}

trait PagingTaskArgs {
  def offset: Int = 0
  def limit: Int = -1
}

trait ParallelismTaskArgs {
  def parallelism: Option[Int]
  def processBatchSleep: Option[FiniteDuration]
}
