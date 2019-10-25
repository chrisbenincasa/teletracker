//package com.teletracker.consumers
//
//import com.google.api.gax.batching.FlowControlSettings
//import com.google.api.gax.rpc.NotFoundException
//import com.google.cloud.pubsub.v1.{Publisher, Subscriber, SubscriptionAdminClient}
//import com.google.inject.Module
//import com.google.protobuf.ByteString
//import com.google.pubsub.v1.{ProjectSubscriptionName, ProjectTopicName, PubsubMessage, Subscription}
//import com.teletracker.common.inject.{BlockingHttpClientModule, Modules}
//import com.teletracker.consumers.inject.HttpClientModule
//import com.twitter.util.{Await, Time}
//import org.reactivestreams.{Publisher, Subscriber, Subscription}
//import software.amazon.awssdk.services.kms.model.NotFoundException
//import java.util.concurrent.TimeUnit
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.util.control.NonFatal
//
//object TeletrackerConsumerDaemon extends com.twitter.inject.app.App {
//  val projectName = flag("project", "teletracker", "The project name")
//  val subscriptionName =
//    flag("subscription", "test-subscription", "The subscription name")
//  val topicName = flag[String]("topic", "The topic name")
//
//  override protected def modules: Seq[Module] =
//    Modules() ++ Seq(new HttpClientModule)
//
//  override protected def run(): Unit = {
//    val client = SubscriptionAdminClient.create()
//    val publisher = Publisher.newBuilder(topicName()).build()
//
//    val projectSubscriptionName = ProjectSubscriptionName
//      .of(projectName(), subscriptionName())
//
//    val subscription = try {
//      client.getSubscription(projectSubscriptionName)
//    } catch {
//      case _: NotFoundException =>
//        client
//          .createSubscription(
//            Subscription
//              .newBuilder()
//              .setEnableMessageOrdering(true)
//              .setName(
//                projectSubscriptionName.toString
//              )
//              .setTopic(
//                ProjectTopicName
//                  .of(projectName(), topicName())
//                  .toString
//              )
//              .build()
//          )
//    }
//
//    val receiver = injector.instance[TeletrackerTaskQueueReceiver]
//
//    val subscriber = Subscriber
//      .newBuilder(
//        subscription.getName,
//        receiver
//      )
//      .setFlowControlSettings(
//        FlowControlSettings
//          .newBuilder()
//          .setMaxOutstandingElementCount(1L)
//          .build()
//      )
//      .build()
//
//    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
//      import io.circe.syntax._
//      override def run(): Unit = {
//        receiver.getUnexecutedTasks.foreach(message => {
//          try {
//            publisher
//              .publish(
//                PubsubMessage
//                  .newBuilder()
//                  .setData(
//                    ByteString.copyFrom(message.asJson.noSpaces.getBytes())
//                  )
//                  .build()
//              )
//              .get()
//          } catch {
//            case NonFatal(e) =>
//              logger.error(
//                s"Could not republish message: $message during shutdown",
//                e
//              )
//          }
//        })
//        subscriber.stopAsync().awaitTerminated(30, TimeUnit.SECONDS)
//      }
//    }))
//
//    subscriber.startAsync()
//
//    Await.ready(this, Time.Top - Time.now)
//  }
//}
