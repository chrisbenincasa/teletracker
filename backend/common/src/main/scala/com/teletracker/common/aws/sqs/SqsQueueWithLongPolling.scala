package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.EventBase
import io.circe.Codec
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.util.UUID
import com.teletracker.common.util.Functions._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/**
  *
  * @param sqs               The SQS queue client
  * @param url               The URL of the queue. Example: http://sqs.us-east-2.amazonaws.com/123456789012/MyQueue
  * @param waitTimeInSeconds The duration (in seconds) for which the call will wait for a message to
  *                          arrive in the queue before returning. Defaults to 5
  *                          Amazon actually recommends setting this to the max of 20 seconds, but that seems awfully long, especially
  *                          if we call dequeue with a count > max batch size.
  *                          http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-long-polling.html
  * @param executionContext  Implicit execution context
  * @tparam T
  */
class SqsQueueWithLongPolling[T <: EventBase: Manifest](
  sqs: SqsAsyncClient,
  url: String,
  waitTimeInSeconds: Int = 5
)(implicit executionContext: ExecutionContext,
  codec: Codec[T])
    extends SqsQueue[T](sqs, url) {

  override protected def getReceiveMessageRequest(
    url: String,
    maxNumberOfMessages: Int,
    waitTime: Duration,
    attemptId: Option[UUID] = None
  ): ReceiveMessageRequest = {
    ReceiveMessageRequest
      .builder()
      .queueUrl(url)
      .waitTimeSeconds(waitTimeInSeconds)
      .maxNumberOfMessages(maxNumberOfMessages)
      .applyOptional(attemptId)(
        (builder, id) => builder.receiveRequestAttemptId(id.toString)
      )
      .build()
  }
}
