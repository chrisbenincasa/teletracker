package com.teletracker.common.inject

import com.google.inject.{Provides, Singleton}
import com.teletracker.common.util.execution.{
  ExecutionContextProvider,
  ProvidedExecutionContext
}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.TwitterModule
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.concurrent.ExecutionContext

class ExecutionContextModule(implicit executionContext: ExecutionContext)
    extends TwitterModule {
  @Provides
  @Singleton
  def providedContext: ProvidedExecutionContext =
    ExecutionContextProvider.provider.of(executionContext)

  @Provides
  @Singleton
  def rawContext(
    providedExecutionContext: ProvidedExecutionContext
  ): ExecutionContext = {
    providedExecutionContext
  }

  @Provides
  @Singleton
  @RetryScheduler
  def retryScheduler: ScheduledExecutorService =
    Executors.newScheduledThreadPool(
      5,
      new NamedPoolThreadFactory("retry-scheduler")
    )
}
