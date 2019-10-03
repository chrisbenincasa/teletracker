package com.teletracker.common.inject

import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.storage.{Storage, StorageOptions}
import com.google.inject.{Provides, Singleton}
import com.google.pubsub.v1.ProjectTopicName
import com.twitter.inject.TwitterModule

class GoogleModule extends TwitterModule {
  @Provides
  @Singleton
  def storageClient: Storage = StorageOptions.getDefaultInstance.getService

  @Provides
  def kmsClient: KeyManagementServiceClient =
    KeyManagementServiceClient.create()

  @Provides
  def taskQueuePublisher =
    Publisher
      .newBuilder(ProjectTopicName.of("teletracker", "teletracker-task-queue"))
      .build()
}
