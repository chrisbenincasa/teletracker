package com.teletracker.consumers

import com.google.api.gax.batching.FlowControlSettings
import com.google.api.gax.rpc.NotFoundException
import com.google.cloud.pubsub.v1.{
  AckReplyConsumer,
  MessageReceiver,
  Subscriber,
  SubscriptionAdminClient
}
import com.google.inject.{Injector, Module}
import com.google.pubsub.v1.{
  ProjectSubscriptionName,
  ProjectTopicName,
  PubsubMessage,
  Subscription
}
import com.teletracker.common.inject.{AsyncModules, BlockingHttpClientModule}
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskRunner}
import com.twitter.util.{Await, Time}
import io.circe.Json
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.util.concurrent.{Executor, Executors, TimeUnit}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.control.NonFatal

object TeletrackerConsumer extends com.twitter.inject.app.App {
  val projectName = flag("project", "teletracker", "The project name")
  val subscriptionName =
    flag("subscription", "test-subscription", "The subscription name")
  val topicName = flag("topic", "The topic name")

  override protected def modules: Seq[Module] =
    AsyncModules() ++ Seq(new BlockingHttpClientModule)

  override protected def run(): Unit = {
    val client = SubscriptionAdminClient.create()

    val projectSubscriptionName = ProjectSubscriptionName
      .of(projectName(), subscriptionName())

    val subscription = try {
      client.getSubscription(projectSubscriptionName)
    } catch {
      case _: NotFoundException =>
        client
          .createSubscription(
            Subscription
              .newBuilder()
              .setEnableMessageOrdering(true)
              .setName(
                projectSubscriptionName.toString
              )
              .setTopic(
                ProjectTopicName
                  .of(projectName(), topicName())
                  .toString
              )
              .build()
          )
    }

    val subscriber = Subscriber
      .newBuilder(
        subscription.getName,
        injector.instance[TeletrackerTaskQueueReceiver]
      )
      .setFlowControlSettings(
        FlowControlSettings
          .newBuilder()
          .setMaxOutstandingElementCount(1L)
          .build()
      )
      .build()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        subscriber.stopAsync().awaitTerminated(30, TimeUnit.SECONDS)
      }
    }))

    subscriber.startAsync()

    Await.ready(this, Time.Top - Time.now)
  }
}

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
