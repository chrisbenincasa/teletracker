package com.teletracker.common.pubsub

import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}

trait QueueReader[T <: EventBase] extends QueueIdentity {

  /**
    * Pull N messages off of the queue
    *
    *
    * @param count
    * @return
    */
  def dequeue(
    count: Int,
    waitTime: Duration
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

trait QueueWriter[T <: EventBase] extends QueueIdentity {

  /**
    * Send a message to the queue
    *
    * @param message
    */
  def queue(message: T): Future[Option[T]]

  /**
    * Send many messages to the queue
    *
    * @param messages
    */
  def batchQueue(
    messages: List[T],
    messageGroupId: Option[String] = None
  ): Future[List[T]]
}

trait QueueIdentity {
  def name: String

  def url: String
}

trait Queue[T <: EventBase] extends QueueReader[T] with QueueWriter[T]
