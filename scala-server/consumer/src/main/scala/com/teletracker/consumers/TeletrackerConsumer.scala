package com.teletracker.consumers

import com.google.api.gax.rpc.NotFoundException
import com.google.cloud.pubsub.v1.{
  AckReplyConsumer,
  MessageReceiver,
  Subscriber,
  SubscriptionAdminClient
}
import com.google.inject.Module
import com.google.pubsub.v1.{
  ProjectSubscriptionName,
  ProjectTopicName,
  PubsubMessage,
  Subscription
}
import com.teletracker.common.inject.Modules
import com.twitter.util.{Await, Time}
import scala.concurrent.ExecutionContext.Implicits.global

object TeletrackerConsumer extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val projectName = "teletracker"

    val client = SubscriptionAdminClient.create()

    val subscriptionName = ProjectSubscriptionName
      .of(projectName, "test-subscription")

    val subscription = try {
      client.getSubscription(subscriptionName)
    } catch {
      case e: NotFoundException =>
        client
          .createSubscription(
            Subscription
              .newBuilder()
              .setName(
                subscriptionName.toString
              )
              .setTopic(
                ProjectTopicName
                  .of(projectName, "hbo-scrape-trigger")
                  .toString
              )
              .build()
          )
    }

    val subscriber = Subscriber
      .newBuilder(
        subscription.getName,
        new Receiver
      )
      .build()

    subscriber.startAsync()

    Await.ready(this, Time.Top - Time.now)
  }
}

class Receiver extends MessageReceiver {
  override def receiveMessage(
    message: PubsubMessage,
    consumer: AckReplyConsumer
  ): Unit = {
    println(message)

    consumer.ack()
  }
}
