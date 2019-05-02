package com.teletracker.service.util.execution

import java.util.ServiceLoader
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

class MissingExecutionContextException extends Exception(
  s"""A provided execution context was missing!
     |Make sure your META-INF/services/${classOf[ExecutionContextProvider].getName}
     |file exists and has a valid entry that can be load.
     |This is critical for trace id propagation""".stripMargin
)

object ExecutionContextProvider {
  lazy val provider: ExecutionContextProvider = {
    Option(ServiceLoader.load(classOf[ExecutionContextProvider])).
      map(_.asScala).
      getOrElse(Nil).
      headOption.
      getOrElse(throw new MissingExecutionContextException)
  }
}

/**
 * Marker interfaces to provide contexts with custom logic. This
 * forces users to make sure to use the execution context providers that support request tracing
 * and maybe other tooling
 */
trait ProvidedExecutionContext extends ExecutionContext

trait ProvidedExecutorService extends ExecutorService {
  def asExecutionContext: ExecutionContextExecutorService = {
    ExecutionContext.fromExecutorService(this)
  }
}

trait ProvidedSchedulerService extends ScheduledExecutorService {
  def asExecutionContext: ExecutionContextExecutorService = {
    ExecutionContext.fromExecutorService(this)
  }
}

/**
 * A context provider contract
 */
trait ExecutionContextProvider {
  def of(context: ExecutionContext): ProvidedExecutionContext

  def of(executorService: ExecutorService): ProvidedExecutorService

  def of(scheduledExecutorService: ScheduledExecutorService): ProvidedSchedulerService
}