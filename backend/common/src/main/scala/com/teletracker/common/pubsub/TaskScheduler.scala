package com.teletracker.common.pubsub

import scala.concurrent.Future

trait TaskScheduler {
  def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage,
    groupId: Option[String] = None
  ): Future[Unit]

  def schedule(
    teletrackerTaskQueueMessage: List[
      (TeletrackerTaskQueueMessage, Option[String])
    ]
  ): Future[Unit]
}
