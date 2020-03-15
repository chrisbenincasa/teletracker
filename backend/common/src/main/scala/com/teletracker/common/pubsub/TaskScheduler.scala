package com.teletracker.common.pubsub

import scala.concurrent.Future

trait TaskScheduler {
  def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage
  ): Future[Unit]

  def schedule(
    teletrackerTaskQueueMessage: List[TeletrackerTaskQueueMessage]
  ): Future[Unit]
}
