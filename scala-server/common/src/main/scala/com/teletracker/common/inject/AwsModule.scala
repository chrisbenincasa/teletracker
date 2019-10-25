package com.teletracker.common.inject

import com.google.inject.Provides
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient

class AwsModule extends TwitterModule {
  @Provides
  @Singleton
  def s3Client: S3Client = S3Client.create()

  @Provides
  @Singleton
  def sqsClient: SqsClient = SqsClient.create()
}
