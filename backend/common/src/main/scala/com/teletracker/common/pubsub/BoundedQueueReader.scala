package com.teletracker.common.pubsub

import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait BoundedQueueReader[UpperBound <: SettableReceiptHandle]
    extends QueueIdentity {

  /**
    * Pull N messages off of the queue
    *
    *
    * @param count
    * @return
    */
  def dequeue[T <: UpperBound](
    count: Int,
    waitTime: FiniteDuration
  )(implicit decoder: Decoder[T]
  ): Future[List[T]]

  /**
    * Ack messages in order to remove them from the queue
    *
    * @param receiptHandles
    */
  def remove(receiptHandles: List[String]): Future[Unit]

  /**
    *
    * @param receiptHandles
    * @param newVisibilityTimeout
    */
  def changeVisibility(
    receiptHandles: List[String],
    newVisibilityTimeout: FiniteDuration
  ): Future[List[ChangeMessageVisibilityBatchResponse]]

  /**
    *
    * @param receiptHandles
    */
  def clearVisibility(receiptHandles: List[String]): Future[Unit]
}
