package com.teletracker.common.aws.sqs.worker.poll

import com.teletracker.common.pubsub.{EventBase, QueueReader}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.execution.{
  ExecutionContextProvider,
  ProvidedSchedulerService
}
import software.amazon.awssdk.services.sqs.model.SqsException
import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

case class HeartbeatConfig(
  heartbeat_frequency: FiniteDuration,
  visibility_timeout: FiniteDuration)

object Heartbeat {
  final val INVALID_PARAMETER_ERROR_CODE = 400
}

class Heartbeat[T <: EventBase](
  item: T,
  queue: QueueReader[T],
  config: HeartbeatConfig,
  scheduler: ProvidedSchedulerService = ExecutionContextProvider.provider
    .of(Executors.newSingleThreadScheduledExecutor())) {
  private var future: Option[ScheduledFuture[_]] = None

  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def start(): Unit = {
    logger.debug("Starting heartbeat")

    future = Some(
      scheduler.scheduleWithFixedDelay(
        new Runnable {
          override def run() = {
            Try {
              logger.debug(
                s"Heartbeating ${item.receipt_handle.get} to add another ${config.visibility_timeout.toSeconds} seconds"
              )

              val response = queue
                .changeVisibility(
                  List(item.receipt_handle.get),
                  config.visibility_timeout
                )
                .await()

              // its not clear if  invalid comes back from a standard response
              // or if its thrown as an exception as different testing has shown different things
              // docker containers do one thing, sqs live does another, hence the double
              // handling of invalid messages in both here and below in the exception handling

              val failedEntries = response.headOption
                .map(_.failed().asScala.toList)
                .getOrElse(Nil)

              if (failedEntries.nonEmpty) {
                logger.error(
                  s"Failed entries from heartbeat: ${failedEntries.mkString(",")}"
                )
              }

              val invalidMessage =
                failedEntries
                  .exists(
                    _.code() == "ReceiptHandleIsInvalid"
                  )

              if (invalidMessage) {
                handleInvalidReceipt()
              }
            } match {
              case Failure(exception: SqsException)
                  if exception
                    .statusCode() == Heartbeat.INVALID_PARAMETER_ERROR_CODE =>
                handleInvalidReceipt()

              case Failure(exception) =>
                logger.error(
                  s"Handle ${item.receipt_handle} failed to heartbeat! It may DLQ. Will retry...",
                  exception
                )

              case Success(_) =>
                logger.debug(s"Heartbeat ${item.receipt_handle} successful")
            }
          }
        },
        0,
        config.heartbeat_frequency.toMillis, // repeat
        TimeUnit.MILLISECONDS
      )
    )
  }

  private def handleInvalidReceipt(): Unit = {
    logger.warn(
      s"Receipt id ${item.receipt_handle} has become invalid. It may have already been ack'd, or heartbeating failed and the message handle is longer valid"
    )

    complete()
  }

  def complete(): Unit = {
    logger.debug("Heartbeat completed")

    future.foreach(_.cancel(false))
  }
}
