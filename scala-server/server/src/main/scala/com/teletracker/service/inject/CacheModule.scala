package com.teletracker.service.inject

import com.google.common.cache.{Cache, CacheBuilder}
import com.google.inject.Provides
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

class CacheModule extends TwitterModule {
  @Provides
  @Singleton
  @RecentlyProcessedCollections
  def recentlyProcessedCollectionCache: Cache[Integer, java.lang.Boolean] =
    CacheBuilder
      .newBuilder()
      .expireAfterWrite(1, TimeUnit.DAYS)
      .build[java.lang.Integer, java.lang.Boolean]()
}
