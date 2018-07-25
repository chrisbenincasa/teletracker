package com.chrisbenincasa.services.teletracker.inject

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.chrisbenincasa.services.teletracker.db.CustomPostgresProfile
import com.chrisbenincasa.services.teletracker.db.model.{ExternalSource, GenreType, OfferType, ThingType}
import com.chrisbenincasa.services.teletracker.util.execution.ExecutionContextProvider
import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule
import javax.inject.Inject
import slick.jdbc.{DriverDataSource, JdbcProfile}
import slick.util.AsyncExecutor
import scala.concurrent.ExecutionContext

class DbModule extends TwitterModule {
  @Provides
  @Singleton
  def dataSource(config: TeletrackerConfig)(implicit executionContext: ExecutionContext): javax.sql.DataSource = {
    new DriverDataSource(
      url = config.db.url,
      user = config.db.user,
      password = config.db.password,
      driverObject = config.db.driver
    )
  }

  @Provides
  @Singleton
  def profile()(implicit executionContext: ExecutionContext): JdbcProfile = {
    CustomPostgresProfile
  }

  @Provides
  @Singleton
  def customProfile()(implicit executionContext: ExecutionContext): CustomPostgresProfile = {
    CustomPostgresProfile
  }
}

class DbProvider @Inject()(
  val dataSource: javax.sql.DataSource
)(implicit executionContext: ExecutionContext) {
  val driver: CustomPostgresProfile = CustomPostgresProfile

  import driver.api._

  private lazy val db = Database.forDataSource(
    dataSource,
    None,
    executor = CustomAsyncExecutor(executionContext)
  )

  def getDB: Database = db
}

class DbImplicits @Inject()(val profile: JdbcProfile) {
  import profile.api._

  implicit val externalSourceMapper = MappedColumnType.base[ExternalSource, String](_.getName, ExternalSource.fromString)
  implicit val offerTypeMapper = MappedColumnType.base[OfferType, String](_.getName, OfferType.fromString)
  implicit val genreTypeMapper = MappedColumnType.base[GenreType, String](_.getName, GenreType.fromString)
  implicit val thingTypeMapper = MappedColumnType.base[ThingType, String](_.getName, ThingType.fromString)
}

object CustomAsyncExecutor {
  def apply(_executionContext: ExecutionContext, onClose: => Unit = {}): AsyncExecutor = new AsyncExecutor {
    override def executionContext: ExecutionContext = ExecutionContextProvider.provider.of(_executionContext)

    override def close(): Unit = onClose
  }
}