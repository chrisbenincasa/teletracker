package com.teletracker.common.db.access

import com.teletracker.common.db.{BaseDbProvider, DbMonitoring}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HealthCheckDbAccess @Inject()(
  val provider: BaseDbProvider,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {

  import provider.driver.api._

  def ping: Future[Unit] = {
    run {
      sql"select 1;".as[Int]
    }.map(_ => {})
  }
}
