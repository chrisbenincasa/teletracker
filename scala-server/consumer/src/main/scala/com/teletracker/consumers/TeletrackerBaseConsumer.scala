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
import com.teletracker.common.inject.{BlockingHttpClientModule, Modules}
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

object TeletrackerBaseConsumer extends com.twitter.inject.app.App {
  val projectName = flag("project", "teletracker", "The project name")
  val subscriptionName =
    flag("subscription", "test-subscription", "The subscription name")
  val topicName = flag("topic", "The topic name")

  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new BlockingHttpClientModule)

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
