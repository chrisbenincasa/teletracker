package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.{EventBase, FailedMessage, Queue}
import io.circe.Codec
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SqsQueue {
  val MAX_MESSAGE_BATCH_SIZE = 10

  val COUNTER_KEY_BASE = "objects.queues"

  val MAX_BATCH_SIZE_BYTES = 256 * 1024
}

private case class EntryWithSize[T](
  entry: SendMessageBatchRequestEntry,
  data: T,
  bytes: Long)

private case class BatchGroups[T](group: List[T])

/**
  * An abstraction over an AWS SQS Queue
  *
  * @param sqs              The SQS queue client
  * @param url               The URL of the queue. Example: http://sqs.us-east-2.amazonaws.com/123456789012/MyQueue
  * @param executionContext  Implicit execution context
  * @tparam T
  */
class SqsQueue[T <: EventBase: Manifest: Codec](
  sqs: SqsAsyncClient,
  override val url: String,
  val dlq: Option[SqsQueue[T]] = None
)(implicit executionContext: ExecutionContext)
    extends SqsQueueBase(sqs, url)
    with Queue[T] {

  /**
    * Send a message to the queue
    *
    * @param message
    */
  override def queue(
    message: T,
    messageGroupId: Option[String]
  ): Future[Option[FailedMessage[T]]] = {
    batchQueue(List(message)).map(_.headOption)
  }

  /**
    * Send many messages to the queue
    *
    * @param messages
    */
  override def batchQueue(
    messages: List[T],
    messageGroupId: Option[String] = None
  ): Future[List[FailedMessage[T]]] = {
    batchQueueAsyncImpl(messages, messageGroupId)
  }

  /**
    * Pull N messages off of the queue
    *
    *
    * @param count
    * @return
    */
  override def dequeue(
    count: Int,
    waitTime: Duration = 1 second
  ): Future[List[T]] = {
    dequeueImpl(
      count,
      waitTime,
      (message: T, sqsMessage: Message) => {
        message.receipt_handle = Some(sqsMessage.receiptHandle())
        message.message_group_id = Option(
          sqsMessage
            .attributes()
            .get(MessageSystemAttributeName.MESSAGE_GROUP_ID)
        )
      }
    )
  }

  /**
    * Asynchronously ack messages in order to remove them from the queue
    *
    * @param receiptHandles
    * @return
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
    * Returns a mapping of attribute names to values for the queue.
    *
    * @param attributesToGet Optionally, specify which options to retrieve. By default, all options will be retrieved.
    * @return
    */
  def getQueueAttributes(
    attributesToGet: List[QueueAttributeName] = List(QueueAttributeName.ALL)
  ): Future[Map[QueueAttributeName, String]] = {
    val req =
      GetQueueAttributesRequest
        .builder()
        .queueUrl(url)
        .attributeNames(attributesToGet.asJavaCollection)
        .build()

    sqs
      .getQueueAttributes(req)
      .toScala
      .map(_.attributes().asScala.toMap.flatMap {
        case (k, v) => Try(k).toOption.map(_ -> v)
      })
  }

  /**
    * Retrieves approximate number of messages in this queue
    *
    * @return
    */
  def getApproximateSize(): Future[Option[Int]] = {
    getQueueAttributes(List(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
      .map(result => {
        result
          .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
          .flatMap(sizeStr => {
            Try(sizeStr.toInt).toOption
          })
      })
  }
}
