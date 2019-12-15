package com.teletracker.common.aws.sqs

import com.teletracker.common.aws.sqs.worker.Queue
import com.teletracker.common.pubsub.EventBase
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.Functions._
import io.circe.Codec
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model._
import java.util.UUID
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

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
  val url: String
)(implicit executionContext: ExecutionContext)
    extends Queue[T] {

  import SqsQueue._
  import io.circe.parser._
  import io.circe.syntax._

  private lazy val logger = LoggerFactory.getLogger(getClass)

  // Grab the queue name from the end of the URL. Example: http://sqs.us-east-2.amazonaws.com/123456789012/MyQueue yields MyQueue
  lazy val queue =
    url.split("/").lastOption.getOrElse("unknown")

  protected val SENT_TIMESTAMP_ATTRIBUTE = "SentTimestamp"
  private lazy val enqueueKey = "%s.%s.enqueue".format(COUNTER_KEY_BASE, queue)
  private lazy val dequeueKey = "%s.%s.dequeue".format(COUNTER_KEY_BASE, queue)
  private lazy val removeKey = "%s.%s.remove".format(COUNTER_KEY_BASE, queue)
  private lazy val setVisibility =
    "%s.%s.setVisibility".format(COUNTER_KEY_BASE, queue)
  private lazy val enqueueFailedKey =
    "%s.%s.enqueueFailed".format(COUNTER_KEY_BASE, queue)
  private lazy val timeInQueueKey =
    "%s.%s.secondsInQueue".format(COUNTER_KEY_BASE, queue)
  private lazy val deserializationFailureCountKey =
    "%s.%s.deserializationFailed".format(COUNTER_KEY_BASE, queue)

  private lazy val isFifo = url.endsWith(".fifo")

  override def name: String =
    url.split("/").lastOption.getOrElse("unknown")

  /**
    * Send a message to the queue
    *
    * @param message
    */
  override def queue(message: T): Future[Option[T]] = {
    batchQueue(List(message)).map(_.headOption)
  }

  /**
    * Send message to the queue asynchronously
    *
    * @param message
    * @return
    */
  override def queueAsync(message: T): Future[Option[T]] = {
    batchQueueAsync(List(message)).map(_.headOption)
  }

  /**
    * Send many messages to the queue
    *
    * @param messages
    */
  override def batchQueue(
    messages: List[T],
    messageGroupId: Option[String] = None
  ): Future[List[T]] = {
    batchQueueAsync(messages, messageGroupId)
  }

  private def createBatchQueueGroups(
    messages: List[T],
    messageGroupId: Option[String]
  ): Iterable[BatchGroups[EntryWithSize[T]]] = {
    val entries = messages.zipWithIndex.map {
      case (m, i) =>
        val message = serializer(m)

        EntryWithSize(
          SendMessageBatchRequestEntry
            .builder()
            .id((i + 1).toString)
            .messageBody(message)
            .applyIf(isFifo)(
              _.messageGroupId(messageGroupId.getOrElse("default"))
            )
            // No deduplication happening here...
            .applyIf(isFifo)(
              _.messageDeduplicationId(UUID.randomUUID().toString)
            )
            .build(),
          m,
          message.getBytes("UTF-8").length
        )
    }

    def partition(
      data: List[EntryWithSize[T]]
    ): List[BatchGroups[EntryWithSize[T]]] = {
      def isBeyondMax = data.map(_.bytes).sum >= SqsQueue.MAX_BATCH_SIZE_BYTES

      def eachLessThanMax =
        data.forall(_.bytes <= SqsQueue.MAX_BATCH_SIZE_BYTES)

      if (!isBeyondMax) {
        // if all the messages are good, return them
        val result = BatchGroups(data)

        List(result)
      } else if (eachLessThanMax) {
        // there are no jumbo items that are beyond the max themselves
        // so clearly the sum of all of them together is too much
        // split the batch in half and verify its size.
        // if the size is still large, split each partition in half again

        val (h, t) = data.splitAt(data.length / 2)

        partition(h) ++ partition(t)
      } else {
        val (itemsBiggerThanMax, lessThanMax) =
          data.partition(_.bytes >= SqsQueue.MAX_BATCH_SIZE_BYTES)

        // items bigger than max are just always gonna fail anyways
        // so skip them from the partitioner and each into their own group
        // so that they fail independently
        partition(lessThanMax) ++ itemsBiggerThanMax.map(
          item => BatchGroups(List(item))
        )
      }
    }

    // create groups of max message batch size, and then make sure each group is within the
    // correct tolerances.  one group of 10 may create a list of other smaller groups
    // depending on whats in the group
    entries
      .grouped(SqsQueue.MAX_MESSAGE_BATCH_SIZE)
      .flatMap(partition)
      .toIterable
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
  override def batchQueueAsync(
    messages: List[T],
    messageGroupId: Option[String] = None
  ): Future[List[T]] = {
    val entries = createBatchQueueGroups(messages, messageGroupId)

    def batchQueueAsyncInner(
      groups: Iterable[List[SendMessageBatchRequestEntry]],
      failed: List[T] = Nil
    ): Future[List[T]] = {
      if (groups.isEmpty) {
        return Future.successful(failed)
      }

      val group = groups.head

      val request = SendMessageBatchRequest
        .builder()
        .queueUrl(url)
        .entries(group.asJava)
        .build()

      sqs
        .sendMessageBatch(request)
        .toScala
        .flatMap(_ => {

          batchQueueAsyncInner(groups.tail, failed)
        })
        .recoverWith {
          case NonFatal(throwable) => {
            logger.error(
              "Encountered batchQueueAsync failure for batch",
              throwable
            )

            val failedMessages = group.map(_.messageBody()).map(deserialize)

            batchQueueAsyncInner(groups.tail, failed ++ failedMessages)
          }
        }
    }

    batchQueueAsyncInner(entries.map(_.group.map(_.entry)))
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
    def dequeueInner(
      remainingCount: Int = count,
      accum: List[T] = Nil
    ): Future[List[T]] = {
      val maxNumberOfMessages = math.min(remainingCount, MAX_MESSAGE_BATCH_SIZE)

      val request = getReceiveMessageRequest(url, maxNumberOfMessages, waitTime)

      sqs
        .receiveMessage(request)
        .toScala
        .map(_.messages().asScala.toList)
        .map(
          _.flatMap(m => {
            try {
              val message = deserialize(m.body())

              message.receipt_handle = Some(m.receiptHandle())
              Some(message)
            } catch {
              case ex: Exception =>
                logger.error(
                  s"Unable to deserialize message from queue [$queue], JSON was: ${m.body()}",
                  ex
                )
                // Reset message visibillity, it will be processed a few more times before
                // hitting the DLQ.
                clearVisibility(List(m.receiptHandle()))

                None
            }
          })
        )
        .flatMap(messages => {

          val newAccum = accum ++ messages

          // messages are still coming in and we haven't reached our count size yet
          if (newAccum.size < count && messages.nonEmpty) {
            dequeueInner(count - newAccum.size, newAccum)
          } else {
            // either the site is met, or we got an empty response
            Future.successful(newAccum)
          }
        })

    }

    if (count <= 0) {
      Future.successful(Nil)
    } else {
      dequeueInner()
    }
  }

  /**
    * Asynchronously ack messages in order to remove them from the queue
    *
    * @param receiptHandles
    * @return
    */
  override def remove(receiptHandles: List[String]): Future[Unit] = {
    SequentialFutures
      .batchedIterator(receiptHandles.iterator, 10)(handles => {
        val entries = handles.toList.zipWithIndex.map {
          case (rh, i) =>
            DeleteMessageBatchRequestEntry
              .builder()
              .id(i.toString)
              .receiptHandle(rh)
              .build()
        }
        val request = DeleteMessageBatchRequest
          .builder()
          .queueUrl(url)
          .entries(entries.asJava)
          .build()

        sqs.deleteMessageBatch(request).toScala.map(_ :: Nil)
      })
      .map(_ => {})
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
    require(newVisibilityTimeout.toSeconds <= Int.MaxValue)

    SequentialFutures.batchedIteratorAccum(receiptHandles.iterator, 10)(
      handles => {
        val entries = handles.zipWithIndex.map {
          case (rh, i) =>
            ChangeMessageVisibilityBatchRequestEntry
              .builder()
              .id(i.toString)
              .receiptHandle(rh)
              .visibilityTimeout(newVisibilityTimeout.toSeconds.toInt)
              .build()
        }

        val request =
          ChangeMessageVisibilityBatchRequest
            .builder()
            .queueUrl(url)
            .entries(entries.asJavaCollection)
            .build()

        sqs.changeMessageVisibilityBatch(request).toScala.map(List(_))
      }
    )
  }

  /**
    *
    * @param receiptHandles
    */
  override def clearVisibility(receiptHandles: List[String]): Future[Unit] = {
    changeVisibility(receiptHandles, 0 seconds).map(_ => {})
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

  protected def getReceiveMessageRequest(
    url: String,
    maxNumberOfMessages: Int,
    waitTime: Duration
  ): ReceiveMessageRequest = {
    ReceiveMessageRequest
      .builder()
      .queueUrl(url)
      .waitTimeSeconds(waitTime.toSeconds.toInt)
      .maxNumberOfMessages(maxNumberOfMessages)
      .build()
  }

  private def deserialize(s: String) =
    decode[T](s).right.get // TODO: Handle better

  private def serializer(t: T): String = t.asJson.noSpaces
}
