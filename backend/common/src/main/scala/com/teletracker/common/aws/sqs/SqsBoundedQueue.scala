package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.{
  BoundedQueue,
  FailedMessage,
  SettableGroupId,
  SettableReceiptHandle
}
import io.circe.{Codec, Decoder, Encoder}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  ChangeMessageVisibilityBatchResponse,
  Message,
  MessageSystemAttributeName
}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SqsBoundedQueue[UpperBound <: SettableReceiptHandle with SettableGroupId](
  sqs: SqsAsyncClient,
  override val url: String
)(implicit executionContext: ExecutionContext)
    extends SqsQueueBase(sqs, url)
    with BoundedQueue[UpperBound] {

  /**
    * Pull N messages off of the queue
    *
    * @param count
    * @return
    */
  override def dequeue[T <: UpperBound](
    count: Int,
    waitTime: FiniteDuration
  )(implicit decoder: Decoder[T]
  ): Future[List[T]] = {
    dequeueImpl(
      count,
      waitTime,
      (message: T, sqsMessage: Message) => {
        message.setReceiptHandle(Some(sqsMessage.receiptHandle()))
        message.setMessageGroupId(
          Option(
            sqsMessage
              .attributes()
              .get(MessageSystemAttributeName.MESSAGE_GROUP_ID)
          )
        )
      }
    )
  }

  /**
    * Ack messages in order to remove them from the queue
    *
    * @param receiptHandles
    */
  override def remove(receiptHandles: List[String]): Future[Unit] = {
    removeImpl(receiptHandles)
  }

  /**
    *
    * @param receiptHandles
    * @param newVisibilityTimeout
    */
  override def changeVisibility(
    receiptHandles: List[String],
    newVisibilityTimeout: FiniteDuration
  ): Future[List[ChangeMessageVisibilityBatchResponse]] = {
    changeVisibilityImpl(receiptHandles, newVisibilityTimeout)
  }

  /**
    *
    * @param receiptHandles
    */
  override def clearVisibility(receiptHandles: List[String]): Future[Unit] = {
    clearVisibilityImpl(receiptHandles)
  }

  /**
    * Send message to the queue asynchronously
    *
    * @param message
    * @return
    */
  override def queue[T <: UpperBound](
    message: T,
    messageGroupId: Option[String]
  )(implicit codec: Codec[T]
  ): Future[Option[FailedMessage[T]]] = {
    batchQueue(List(message), messageGroupId).map(_.headOption)
  }

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
  override def batchQueue[T <: UpperBound](
    messages: List[T],
    messageGroupId: Option[String]
  )(implicit enc: Codec[T]
  ): Future[List[FailedMessage[T]]] = {
    batchQueueAsyncImpl(messages, messageGroupId)
  }
}
