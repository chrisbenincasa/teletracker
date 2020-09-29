package com.teletracker.common.inject

import com.google.inject.{Provider, Provides, Singleton}
import com.teletracker.common.util.execution.{
  ExecutionContextProvider,
  ProvidedExecutionContext,
  ProvidedExecutorService,
  ProvidedSchedulerService
}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.TwitterModule
import java.util.concurrent.{
  ExecutorService,
  Executors,
  ScheduledExecutorService,
  ThreadFactory
}
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
  @SingleThreaded
  def providedScheduledSingleThreaded: ProvidedSchedulerService =
    ExecutionContextProvider.provider.of(
      Executors.newSingleThreadScheduledExecutor()
    )

  @Provides
  @SingleThreaded
  def rawScheduledSingleThreaded(
    @SingleThreaded provided: Provider[ProvidedSchedulerService]
  ): ScheduledExecutorService =
    provided.get()

  @Provides
  @Singleton
  @RetryScheduler
  def retryScheduler: ScheduledExecutorService = {
    ExecutionContextProvider.provider.of(
      Executors.newScheduledThreadPool(
        5,
        new NamedPoolThreadFactory("retry-scheduler")
      )
    )
  }
}

abstract class GeneralThreadPoolFactory[T <: ExecutorService](
  protected val factory: (Int, ThreadFactory) => T) {
  def create(
    size: Int,
    name: String
  ): ProvidedExecutorService = {
    ExecutionContextProvider.provider.of(
      factory(
        size,
        new NamedPoolThreadFactory(name)
      )
    )
  }
}

object NamedScheduledThreadPoolFactory
    extends GeneralThreadPoolFactory(Executors.newScheduledThreadPool) {
  override def create(
    size: Int,
    name: String
  ): ProvidedSchedulerService = {
    ExecutionContextProvider.provider.of(
      factory(size, new NamedPoolThreadFactory(name))
    )
  }
}

object NamedFixedThreadPoolFactory
    extends GeneralThreadPoolFactory(Executors.newFixedThreadPool)
