package com.teletracker.tasks.aws

import com.teletracker.common.util.Futures._
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  DeleteMessageBatchRequest,
  DeleteMessageBatchRequestEntry,
  Message,
  ReceiveMessageRequest
}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

class SimpleQueueConsumer @Inject()(
  sqsAsyncClient: SqsAsyncClient
)(implicit executionContext: ExecutionContext) {
  def retrieve(
    sourceQueue: String,
    limit: Int = -1
  )(
    handleBatch: List[Message] => List[String]
  ): Future[Unit] = {
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
                  val acks = handleBatch(messages)

                  val entries = acks.zipWithIndex.map {
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

    retrieveLoop()
  }
}
