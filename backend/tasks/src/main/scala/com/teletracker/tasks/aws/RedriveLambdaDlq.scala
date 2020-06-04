package com.teletracker.tasks.aws

import com.teletracker.common.tasks.UntypedTeletrackerTask
import javax.inject.Inject
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import com.teletracker.common.util.Futures._
import software.amazon.awssdk.services.lambda.model.{
  GetFunctionRequest,
  InvocationType,
  InvokeRequest
}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.arns.Arn
import software.amazon.awssdk.core.SdkBytes
import java.nio.charset.Charset
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext

class RedriveLambdaDlq @Inject()(
  simpleQueueConsumer: SimpleQueueConsumer
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {

  override protected def runInternal(): Unit = {
    val queueUrlFmt = "https://sqs.%s.amazonaws.com/%s/%s"

    val lambdaClient = LambdaAsyncClient.create()

    val functionName = rawArgs.valueOrThrow[String]("function")

    val functionResponse = lambdaClient
      .getFunction(
        GetFunctionRequest
          .builder()
          .functionName(functionName)
          .build()
      )
      .toScala
      .await()

    Option(functionResponse.configuration().deadLetterConfig())
      .foreach(dlqConfig => {
        val arn = Arn.fromString(dlqConfig.targetArn())
        if (arn.service() == "sqs") {
          for {
            region <- arn.region().asScala
            accountId <- arn.accountId().asScala
          } {
            val url =
              queueUrlFmt.format(region, accountId, arn.resource().resource())

            simpleQueueConsumer
              .retrieve(url) {
                batch =>
                  batch.map(message => {
                    lambdaClient
                      .invoke(
                        InvokeRequest
                          .builder()
                          .functionName(functionName)
                          .invocationType(InvocationType.EVENT)
                          .payload(
                            SdkBytes
                              .fromString(
                                message.body(),
                                Charset.defaultCharset()
                              )
                          )
                          .build()
                      )
                      .toScala
                      .await()

                    message.receiptHandle()
                  })
              }
              .await()
          }
        }
      })
  }
}
