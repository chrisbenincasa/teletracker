package com.teletracker.common.db.access

import com.teletracker.common.db.{BaseDbProvider, DbMonitoring}
import java.util.concurrent.RejectedExecutionException
import scala.concurrent.{ExecutionContext, Future}

abstract class AbstractDbAccess(
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext) {
  val provider: BaseDbProvider

  import provider.driver.api._

  def run[R, S <: NoStream, E <: Effect](
    method: String
  )(
    action: DBIOAction[R, S, E]
  ): Future[R] = {
    dbMonitoring.timed(method) {
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
