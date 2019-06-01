package com.teletracker.service.inject

import com.google.inject.Provides
import com.teletracker.service.process.{
  InMemoryFifoProcessQueue,
  ProcessMessage,
  ProcessQueue
}
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class BackgroundProcessorModule extends TwitterModule {
  @Provides
  @Singleton
  def queue(
    implicit executionContext: ExecutionContext
  ): ProcessQueue[ProcessMessage] = new InMemoryFifoProcessQueue()
}
