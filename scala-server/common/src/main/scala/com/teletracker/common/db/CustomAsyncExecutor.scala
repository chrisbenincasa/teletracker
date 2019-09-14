package com.teletracker.common.db

import com.teletracker.common.util.execution.ExecutionContextProvider
import slick.util.AsyncExecutor
import scala.concurrent.ExecutionContext

object CustomAsyncExecutor {
  def apply(
    _executionContext: ExecutionContext,
    onClose: => Unit = {}
  ): AsyncExecutor = new AsyncExecutor {
    override def executionContext: ExecutionContext =
      ExecutionContextProvider.provider.of(_executionContext)

    override def close(): Unit = onClose
  }
}
