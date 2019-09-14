package com.teletracker.common.db

import com.google.inject.Singleton
import com.teletracker.common.inject.{AsyncPath, SyncPath}
import javax.inject.Inject
import java.io.Closeable
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class BaseDbProvider @Inject()(dataSource: javax.sql.DataSource) {
  protected val fixedPool =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  val driver: CustomPostgresProfile = CustomPostgresProfile

  import driver.api._

  private lazy val db = Database.forDataSource(
    dataSource,
    None,
    executor = CustomAsyncExecutor(fixedPool)
  )

  def getDB: Database = db

  def shutdown(): Unit = {
    Try(dataSource.unwrap(classOf[Closeable])) match {
      case Success(closeable) => closeable.close()
      case Failure(_) =>
        System.err.println(s"${dataSource.getClass} is not Closable!")
    }
  }
}

@Singleton
class SyncDbProvider @Inject()(@SyncPath val dataSource: javax.sql.DataSource)
    extends BaseDbProvider(dataSource)

@Singleton
class AsyncDbProvider @Inject()(@AsyncPath val dataSource: javax.sql.DataSource)
    extends BaseDbProvider(dataSource)
