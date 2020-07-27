package com.teletracker.consumers.redis

import com.teletracker.common.pubsub.{EventBase, QueueReader, QueueWriter}
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.twitter.finagle.redis.Client
import com.twitter.io.Buf
import io.circe.Codec
import io.circe.syntax._
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class RedisListQueue[T <: EventBase: Codec](
  key: String,
  client: Client
)(implicit executionContext: ExecutionContext)
    extends QueueReader[T]
    with QueueWriter[T] {
  private val inFlight = new ConcurrentHashMap[String, T]()

  private val logger = LoggerFactory.getLogger(getClass)

  private val keyAsBuf = Buf.Utf8(key)

  /**
    * Pull N messages off of the queue
    *
    * @param count
    * @return
    */
  override def dequeue(
    count: Int,
    waitTime: Duration
  ): Future[List[T]] = {
    AsyncStream
      .fromSeq(0 until count)
      .mapF(_ => {
        client.lPop(keyAsBuf).toScalaFuture()
      })
      .flatMapOption(buf => {
        buf.flatMap(
          b =>
            bufToT(b) match {
              case Failure(exception) =>
                logger.error("Could not deserialize typ", exception)
                None
              case Success(value) =>
                Some(value)
            }
        )
      })
      .withEffect(t => {
        val handle = UUID.randomUUID().toString
        t.setReceiptHandle(Some(handle))
        inFlight.put(handle, t)
      })
      .toList
  }

  /**
    * Ack messages in order to remove them from the queue
    *
    * @param receiptHandles
    */
  override def remove(receiptHandles: List[String]): Future[Unit] = {
    Future {
      receiptHandles.foreach(inFlight.remove(_))
    }
  }

  /**
    *
    * @param receiptHandles
    * @param newVisibilityTimeout
    */
  override def changeVisibility(
    receiptHandles: List[String],
    newVisibilityTimeout: FiniteDuration
  ): Future[List[ChangeMessageVisibilityBatchResponse]] = ???

  /**
    *
    * @param receiptHandles
    */
  override def clearVisibility(receiptHandles: List[String]): Future[Unit] = {
    val requeue =
      receiptHandles.flatMap(handle => Option(inFlight.remove(handle)))
    client.lPush(keyAsBuf, requeue.map(tToBuf)).map(_ => {}).toScalaFuture()
  }

  override def name: String = key

  override def url: String = key

  /**
    * Send a message to the queue
    *
    * @param message
    */
  override def queue(
    message: T,
    messageGroupId: Option[String]
  ): Future[Option[T]] = {
    batchQueue(List(message), messageGroupId).map(_.headOption)
  }

  /**
    * Send many messages to the queue
    *
    * @param messages
    */
  override def batchQueue(
    messages: List[T],
    messageGroupId: Option[String]
  ): Future[List[T]] = {
    client
      .lPush(keyAsBuf, messages.map(tToBuf))
      .map(_ => messages)
      .toScalaFuture()
  }

  private def bufToT(buf: Buf): Try[T] = {
    io.circe.parser
      .decode[T](Buf.decodeString(buf, Charset.forName("utf-8")))
      .toTry
  }

  private def tToBuf(t: T): Buf = {
    Buf.Utf8(t.asJson.noSpaces)
  }
}
