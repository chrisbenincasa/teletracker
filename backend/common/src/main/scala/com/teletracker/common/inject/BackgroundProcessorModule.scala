package com.teletracker.common.inject

import com.google.inject.Provides
import com.teletracker.common.process.{InMemoryFifoProcessQueue, ProcessQueue}
import com.teletracker.common.process.tmdb.TmdbProcessMessage
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class BackgroundProcessorModule extends TwitterModule {
  @Provides
  @Singleton
  def queue(
    implicit executionContext: ExecutionContext
  ): ProcessQueue[TmdbProcessMessage] =
    new InMemoryFifoProcessQueue[TmdbProcessMessage]()
}
