package com.teletracker.common.inject

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.{Key, Provider, Provides, Singleton}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.db.access.GenresDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.util.Slug
import com.teletracker.common.util.execution.ExecutionContextProvider
import com.twitter.inject.TwitterModule
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.circe.Json
import javax.inject.Inject
import slick.jdbc.{DriverDataSource, JdbcProfile}
import slick.util.AsyncExecutor
import java.io.Closeable
import java.util.Properties
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class DbModule extends TwitterModule {

  override protected def configure(): Unit = {
    bind[BaseDbProvider, SyncPath].to(classOf[SyncDbProvider])
    bind[BaseDbProvider, AsyncPath].to(classOf[AsyncDbProvider])
  }

  @Provides
  @Singleton
  @SyncPath
  def dataSource(config: TeletrackerConfig): javax.sql.DataSource = {
    mkDataSource("Sync DB Pool", config)
  }

  @Provides
  @Singleton
  @AsyncPath
  def asyncDataSource(config: TeletrackerConfig): javax.sql.DataSource = {
    mkDataSource("Async DB Pool", config)
  }

  private def mkDataSource(
    poolName: String,
    config: TeletrackerConfig
  ) = {
    val props = new Properties()

    props.setProperty("dataSourceClassName", config.db.driver.getClass.getName)
    props.setProperty("username", config.db.user)
    props.setProperty("password", config.db.password)

    val conf = new HikariConfig()
    conf.setJdbcUrl(config.db.url)
    conf.setUsername(config.db.user)
    conf.setPassword(config.db.password)
    conf.setDataSource(
      new DriverDataSource(
        url = config.db.url,
        user = config.db.user,
        password = config.db.password,
        driverObject = config.db.driver,
        properties = props
      )
    )

    val hikari = new HikariDataSource(conf)
    hikari.setPoolName(poolName)
    hikari.setMaximumPoolSize(3)
    hikari
  }

  @Provides
  @Singleton
  def profile()(implicit executionContext: ExecutionContext): JdbcProfile = {
    CustomPostgresProfile
  }

  @Provides
  @Singleton
  def customProfile(
  )(implicit executionContext: ExecutionContext
  ): CustomPostgresProfile = {
    CustomPostgresProfile
  }
}

abstract class BaseDbProvider(dataSource: javax.sql.DataSource) {
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

class DbImplicits @Inject()(val profile: CustomPostgresProfile) {
  import com.teletracker.common.util.json.circe._
  import io.circe.syntax._
  import profile.api._

  implicit val externalSourceMapper = MappedColumnType
    .base[ExternalSource, String](_.getName, ExternalSource.fromString)
  implicit val offerTypeMapper =
    MappedColumnType.base[OfferType, String](_.getName, OfferType.fromString)
  implicit val presentationTypeMapper = MappedColumnType
    .base[PresentationType, String](_.getName, PresentationType.fromString)
  implicit val genreTypeMapper =
    MappedColumnType.base[GenreType, String](_.getName, GenreType.fromString)
  implicit val genreListTypeMapper =
    MappedColumnType.base[List[GenreType], List[String]](
      _.map(_.getName),
      _.map(GenreType.fromString)
    )
  implicit val thingTypeMapper =
    MappedColumnType.base[ThingType, String](_.getName, ThingType.fromString)
  implicit val actionTypeMapper = MappedColumnType
    .base[UserThingTagType, String](_.getName, UserThingTagType.fromString)
  implicit val slugTypeMapper =
    MappedColumnType.base[Slug, String](_.value, Slug.raw)
  implicit val personAssociationTypeMapper =
    MappedColumnType.base[PersonAssociationType, String](
      _.toString,
      PersonAssociationType.fromString
    )

  implicit val userPrefsToJson = MappedColumnType
    .base[UserPreferences, Json](_.asJson, _.as[UserPreferences].right.get)
  implicit val tagRulesToJson = MappedColumnType
    .base[DynamicListRules, Json](_.asJson, _.as[DynamicListRules].right.get)
}

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
