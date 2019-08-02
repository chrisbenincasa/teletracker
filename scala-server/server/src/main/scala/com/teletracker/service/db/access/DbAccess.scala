package com.teletracker.service.db.access

import com.teletracker.service.inject.DbProvider
import java.util.concurrent.RejectedExecutionException
import scala.concurrent.{ExecutionContext, Future}

abstract class DbAccess(implicit executionContext: ExecutionContext) {
  val provider: DbProvider

  import provider.driver.api._

  def run[R, S <: NoStream, E <: Effect](
    action: DBIOAction[R, S, E]
  ): Future[R] = {
    provider.getDB.run(action).recoverWith {
      case _: RejectedExecutionException =>
        Future.failed(
          SlickDBNoAvailableThreadsException(
            "DB thread pool is busy and queue is full, try again"
          )
        )
    }
  }
}
