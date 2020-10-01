package com.teletracker.common.pubsub

import io.circe.Codec
import scala.concurrent.Future

trait BoundedQueueWriter[UpperBound] extends QueueIdentity {

  /**
    * Send message to the queue asynchronously
    *
    * @param message
    * @return
    */
  def queue[T <: UpperBound](
    message: T,
    messageGroupId: Option[String] = None
  )(implicit enc: Codec[T]
  ): Future[Option[FailedMessage[T]]]

  /**
    * Push items onto the queue in batch, asynchronously
    *
    * Internally, messages are grouped into constant sized buckets and batch written to SQS
    * It's possible some batches may fail. We don't fail the whole function (and leave buckets hanging)
    * but rather accumulate the failed message batches and return them to the caller for further action
    *
    * @param messages
    * @return A sequence of messages whose batch request failed
    */
  def batchQueue[T <: UpperBound](
    messages: List[T],
    messageGroupId: Option[String] = None
  )(implicit enc: Codec[T]
  ): Future[List[FailedMessage[T]]]
}
