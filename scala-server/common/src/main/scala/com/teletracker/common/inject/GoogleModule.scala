package com.teletracker.common.inject

import com.google.cloud.storage.{Storage, StorageOptions}
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule

class GoogleModule extends TwitterModule {
  @Provides
  @Singleton
  def storageClient: Storage = StorageOptions.getDefaultInstance.getService
}
