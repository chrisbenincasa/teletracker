//package com.teletracker.common.inject
//
//class GoogleModule extends TwitterModule {
//  @Provides
//  @Singleton
//  def storageClient: Storage = StorageOptions.getDefaultInstance.getService
//
////  @Provides
////  def kmsClient: KeyManagementServiceClient =
////    KeyManagementServiceClient.create()
//
//  @Provides
//  def taskQueuePublisher =
//    Publisher
//      .newBuilder(ProjectTopicName.of("teletracker", "teletracker-task-queue"))
//      .build()
//}
