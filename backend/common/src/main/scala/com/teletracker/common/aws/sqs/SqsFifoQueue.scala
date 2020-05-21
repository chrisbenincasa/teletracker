package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.{EventBase, Queue}
import io.circe.{Codec, Decoder}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  ChangeMessageVisibilityBatchResponse,
  GetQueueAttributesRequest,
  Message,
  MessageSystemAttributeName,
  QueueAttributeName
}
import java.util.UUID
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class SqsFifoQueue[T <: EventBase: Manifest: Codec](
  sqs: SqsAsyncClient,
  override val url: String,
  val dlq: Option[SqsFifoQueue[T]] = None,
  val defaultGroupId: String
)(implicit executionContext: ExecutionContext)
    extends SqsQueueBase(sqs, url)
    with Queue[T] {
  @deprecated(message = "Use #queue(T, String)", since = "Forever")
  final override def queue(
    message: T,
    messageGroupId: Option[String]
  ): Future[Option[T]] = {
    ???
  }

  final def queue(message: T): Future[Option[T]] =
    queue(message, defaultGroupId)

  final def queue(
    message: T,
    messageGroupId: String
  ): Future[Option[T]] = {
    batchQueue(List(message), messageGroupId).map(_.headOption)
  }

  @deprecated(message = "Use #batchQueue(List[T], String)", since = "forever")
  override def batchQueue(
    messages: List[T],
    messageGroupId: Option[String]
  ): Future[List[T]] = {
    ???
  }

  final def batchQueue(
    messages: List[T],
    messageGroupId: String
  ): Future[List[T]] = {
    batchQueueAsyncImpl(messages, Some(messageGroupId))
  }

  final def batchQueue(messages: List[(T, String)]): Future[List[T]] = {
    batchQueueAsyncImpl(messages.map {
      case (t, str) => t -> Some(str)
    })
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
        message.setReceiptHandle(Some(sqsMessage.receiptHandle()))
        message.setMessageGroupId(
          Option(
            sqsMessage
              .attributes()
              .get(MessageSystemAttributeName.MESSAGE_GROUP_ID)
          )
        )
      },
      attemptId = Some(UUID.randomUUID())
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
