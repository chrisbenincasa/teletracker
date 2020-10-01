package com.teletracker.common.aws.sqs

import com.teletracker.common.aws.sqs.SqsQueue.MAX_MESSAGE_BATCH_SIZE
import com.teletracker.common.pubsub.{FailedMessage, QueueIdentity}
import software.amazon.awssdk.services.sqs.model.{
  ChangeMessageVisibilityBatchRequest,
  ChangeMessageVisibilityBatchRequestEntry,
  ChangeMessageVisibilityBatchResponse,
  DeleteMessageBatchRequest,
  DeleteMessageBatchRequestEntry,
  Message,
  MessageSystemAttributeName,
  QueueAttributeName,
  ReceiveMessageRequest,
  SendMessageBatchRequest,
  SendMessageBatchRequestEntry
}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.execution.SequentialFutures
import io.circe.{Codec, Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

abstract class SqsQueueBase(
  sqs: SqsAsyncClient,
  val url: String
)(implicit executionContext: ExecutionContext)
    extends QueueIdentity {
  protected lazy val logger = LoggerFactory.getLogger(getClass)

  // Grab the queue name from the end of the URL. Example: http://sqs.us-east-2.amazonaws.com/123456789012/MyQueue yields MyQueue
  protected lazy val queueName =
    url.split("/").lastOption.getOrElse("unknown")

  protected lazy val isFifo = url.endsWith(".fifo")

  override def name: String =
    url.split("/").lastOption.getOrElse("unknown")

  protected def batchQueueAsyncImpl[T](
    messages: List[T],
    messageGroupId: Option[String] = None
  )(implicit codec: Codec[T]
  ): Future[List[FailedMessage[T]]] = {
    batchQueueAsyncImpl(messages.map(_ -> messageGroupId))
  }

  protected def batchQueueAsyncImpl[T](
    messages: List[(T, Option[String])]
  )(implicit codec: Codec[T]
  ): Future[List[FailedMessage[T]]] = {
    val entries = createBatchQueueGroups(messages)

    def batchQueueAsyncInner(
      groups: Iterable[BatchGroups[EntryWithSize[T]]],
      failed: List[FailedMessage[T]] = Nil
    ): Future[List[FailedMessage[T]]] = {
      if (groups.isEmpty) {
        Future.successful(failed)
      } else {
        val group = groups.head.group.map(_.entry)
        val messageById = groups.head.group.map {
          case EntryWithSize(entry, data, _) => entry.id() -> data
        }.toMap

        val request = SendMessageBatchRequest
          .builder()
          .queueUrl(url)
          .entries(group.asJava)
          .build()

        sqs
          .sendMessageBatch(request)
          .toScala
          .flatMap(res => {
            val failedMessages = res
              .failed()
              .asScala
              .toList
              .flatMap(failed => {
                messageById
                  .get(failed.id())
                  .map(failedData => {
                    FailedMessage(
                      failedData,
                      failed.message(),
                      Option(failed.senderFault())
                    )
                  })
              })
            batchQueueAsyncInner(groups.tail, failed ++ failedMessages)
          })
          .recoverWith {
            case NonFatal(throwable) => {
              logger.error(
                "Encountered batchQueueAsync failure for batch",
                throwable
              )

              val failedMessages = messageById.values.map(
                message => FailedMessage(message, throwable.getMessage, None)
              )

              batchQueueAsyncInner(groups.tail, failed ++ failedMessages)
            }
          }
      }
    }

    batchQueueAsyncInner(entries)
  }

  protected def dequeueImpl[T: Decoder](
    count: Int,
    waitTime: Duration = 1 second,
    messageUpdater: (T, Message) => Unit,
    attemptId: Option[UUID] = None
  ): Future[List[T]] = {
    def dequeueInner(
      remainingCount: Int = count,
      accum: List[T] = Nil
    ): Future[List[T]] = {
      val maxNumberOfMessages = math.min(remainingCount, MAX_MESSAGE_BATCH_SIZE)

      val request =
        getReceiveMessageRequest(url, maxNumberOfMessages, waitTime, attemptId)

      sqs
        .receiveMessage(request)
        .toScala
        .map(_.messages().asScala.toList)
        .map(
          _.flatMap(m => {
            deserialize(m.body()) match {
              case Success(message) =>
                messageUpdater(message, m)
                Some(message)

              case Failure(exception) =>
                logger.error(
                  s"Unable to deserialize message from queue [$queueName], JSON was: ${m.body()}",
                  exception
                )

                clearVisibilityImpl(List(m.receiptHandle()))
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

  private def createBatchQueueGroups[T: Encoder](
    messages: List[(T, Option[String])]
  ): Iterable[BatchGroups[EntryWithSize[T]]] = {
    val entries = messages.zipWithIndex.map {
      case ((m, groupId), i) =>
        val message = serializer(m)

        EntryWithSize(
          SendMessageBatchRequestEntry
            .builder()
            .id((i + 1).toString)
            .messageBody(message)
            .applyIf(isFifo)(
              _.messageGroupId(groupId.getOrElse("default"))
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

  protected def removeImpl(receiptHandles: List[String]): Future[Unit] = {
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

        sqs
          .deleteMessageBatch(request)
          .toScala
          .map(response => {
            val failedAcks = response.failed().asScala.toList

            if (failedAcks.nonEmpty) {
              logger.warn(
                s"Failed to ack ${failedAcks.size} messages. Original messages: ${failedAcks.map(_.message()).mkString("\n")}"
              )
            }
          })
      })
      .map(_ => {})
  }

  protected def changeVisibilityImpl(
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

  protected def clearVisibilityImpl(
    receiptHandles: List[String]
  ): Future[Unit] = {
    changeVisibilityImpl(receiptHandles, 0 seconds).map(_ => {})
  }

  protected def getReceiveMessageRequest(
    url: String,
    maxNumberOfMessages: Int,
    waitTime: Duration,
    attemptId: Option[UUID]
  ): ReceiveMessageRequest = {
    ReceiveMessageRequest
      .builder()
      .queueUrl(url)
      .waitTimeSeconds(waitTime.toSeconds.toInt)
      .maxNumberOfMessages(maxNumberOfMessages)
      .applyOptional(attemptId)(
        (builder, id) => builder.receiveRequestAttemptId(id.toString)
      )
      .attributeNamesWithStrings(
        MessageSystemAttributeName.MESSAGE_GROUP_ID.toString
      )
      .build()
  }

  protected def deserialize[T: Decoder](s: String): Try[T] =
    decode[T](s).toTry

  protected def serializer[T: Encoder](t: T): String = t.asJson.noSpaces
}
