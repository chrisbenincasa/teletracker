package com.teletracker.tasks.aws

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  DeleteMessageBatchRequest,
  DeleteMessageBatchRequestEntry,
  Message,
  ReceiveMessageRequest,
  SendMessageBatchRequest,
  SendMessageBatchRequestEntry
}
import java.util.UUID
import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class QueueTransfer @Inject()(
  sqsAsyncClient: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val sourceQueue = args.valueOrThrow[String]("source")
    val destinationQueue = args.valueOrThrow[String]("destination")
    val limit = args.valueOrDefault("limit", -1)

    def retrieveLoop(totalProcessed: Int = 0): Future[Unit] = {
      println(s"Processed ${totalProcessed} so far.")
      if (limit < 0 || totalProcessed < limit) {
        sqsAsyncClient
          .receiveMessage(
            ReceiveMessageRequest
              .builder()
              .queueUrl(sourceQueue)
              .maxNumberOfMessages(10)
              .build()
          )
          .toScala
          .flatMap {
            case response if !response.messages().isEmpty =>
              val allMessages = response
                .messages()
                .asScala
                .toList

              println(s"Found ${allMessages.size} from the source. Moving.")

              allMessages
                .grouped(10)
                .foreach(messages => {
                  val response = sqsAsyncClient
                    .sendMessageBatch(
                      SendMessageBatchRequest
                        .builder()
                        .queueUrl(destinationQueue)
                        .entries(messages.map(makeBatchEntry).asJavaCollection)
                        .build()
                    )
                    .toScala
                    .await()

                  val handles = allMessages.map(_.receiptHandle())

                  val entries = handles.zipWithIndex.map {
                    case (rh, i) =>
                      DeleteMessageBatchRequestEntry
                        .builder()
                        .id(i.toString)
                        .receiptHandle(rh)
                        .build()
                  }
                  val request = DeleteMessageBatchRequest
                    .builder()
                    .queueUrl(sourceQueue)
                    .entries(entries.asJava)
                    .build()

                  sqsAsyncClient.deleteMessageBatch(request).toScala.await()
                })

              retrieveLoop(totalProcessed + allMessages.size)
            case _ => Future.unit
          }
      } else {
        Future.unit
      }
    }

    retrieveLoop().await()
  }

  private def makeBatchEntry(message: Message) = {
    SendMessageBatchRequestEntry
      .builder()
      .id(UUID.randomUUID().toString)
      .messageBody(message.body())
      .build()
  }
}
