package com.teletracker.consumers

import com.google.cloud.pubsub.v1.{AckReplyConsumer, MessageReceiver}
import com.google.pubsub.v1.PubsubMessage
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskRunner}
import io.circe.Json
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.control.NonFatal

class TeletrackerTaskQueueReceiver @Inject()(taskRunner: TeletrackerTaskRunner)
    extends MessageReceiver {
  import io.circe.parser._

  private val logger = LoggerFactory.getLogger(getClass)

  @volatile private var runningJob: TeletrackerTask = _

  private val workerPool = Executors.newSingleThreadExecutor()

  override def receiveMessage(
    message: PubsubMessage,
    consumer: AckReplyConsumer
  ): Unit = {
    val messageString = new String(message.getData.toByteArray)
    parse(messageString).flatMap(_.as[TeletrackerTaskQueueMessage]) match {
      case Left(e) =>
        logger.error(s"Could not parse message: $messageString", e)
        consumer.ack()

      case Right(message) =>
        logger.info(s"Got message: $message")
        val reply = try {
          synchronized {
            if (runningJob == null) {
              runningJob = taskRunner
                .getInstance(message.clazz)

              val finishPromise = Promise[Unit]

              workerPool.submit(new Runnable {
                override def run(): Unit = {
                  try {
                    runningJob.run(extractArgs(message.args))
                  } finally {
                    logger.info(s"Finished running ${runningJob.getClass}")
                    finishPromise.success(Unit)
                  }
                }
              })

              finishPromise.future.onComplete(_ => {
                synchronized(runningJob = null)
              })

              Ack
            } else {
              logger.info("Job still running, trying new message later.")

              Nack
            }
          }
        } catch {
          case NonFatal(e) =>
            logger.error(
              s"Error while attempting to run job from message: ${messageString}",
              e
            )

            Ack
        }

        reply match {
          case Ack  => consumer.ack()
          case Nack => consumer.nack()
        }
    }
  }

  sealed private trait Reply
  private case object Ack extends Reply
  private case object Nack extends Reply

  private def extractArgs(args: Map[String, Json]): Map[String, Option[Any]] = {
    args.mapValues(extractValue)
  }

  private def extractValue(j: Json): Option[Any] = {
    j.fold(
      None,
      Some(_),
      x => Some(x.toDouble),
      Some(_),
      v => Some(v.map(extractValue)),
      o => Some(o.toMap.mapValues(extractValue))
    )
  }
}

class JobPool(size: Int) {}
