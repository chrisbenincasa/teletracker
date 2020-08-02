package com.teletracker.tasks.aws

import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.util.FileRotator
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  DeleteMessageBatchRequest,
  DeleteMessageBatchRequestEntry,
  ReceiveMessageRequest
}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

class QueueDump @Inject()(
  sqsAsyncClient: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val sourceQueue = rawArgs.valueOrThrow[String]("source")
    val output = rawArgs.valueOrThrow[String]("outputPath")
    val limit = rawArgs.limit
    val delete = rawArgs.valueOrDefault("delete", false)

    val rotator =
      FileRotator.everyNLines("queue-items", 10000, Some(output))

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

              allMessages.map(_.body().stripLineEnd).foreach(rotator.writeLine)

              if (delete) {
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
              }

              retrieveLoop(totalProcessed + allMessages.size)
            case _ => Future.unit
          }
      } else {
        Future.unit
      }
    }

    retrieveLoop().await()

    rotator.finish()
  }
}
