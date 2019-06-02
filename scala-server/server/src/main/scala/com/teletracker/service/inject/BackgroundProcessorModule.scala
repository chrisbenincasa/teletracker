package com.teletracker.service.inject

import com.google.inject.Provides
import com.teletracker.service.process.tmdb.TmdbProcessMessage
import com.teletracker.service.process.{InMemoryFifoProcessQueue, ProcessQueue}
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
