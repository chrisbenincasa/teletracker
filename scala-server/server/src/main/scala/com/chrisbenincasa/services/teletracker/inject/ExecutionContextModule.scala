package com.chrisbenincasa.services.teletracker.inject

import com.chrisbenincasa.services.teletracker.util.execution.{ExecutionContextProvider, ProvidedExecutionContext}
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule
import scala.concurrent.ExecutionContext

class ExecutionContextModule(implicit executionContext: ExecutionContext) extends TwitterModule {
  @Provides
  @Singleton
  def providedContext: ProvidedExecutionContext = ExecutionContextProvider.provider.of(executionContext)

  @Provides
  @Singleton
  def rawContext(providedExecutionContext: ProvidedExecutionContext): ExecutionContext = {
    providedExecutionContext
  }
}