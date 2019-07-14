package com.teletracker.service.inject

import com.google.inject.{Provides, Singleton}
import com.teletracker.service.config.TeletrackerConfig
import com.teletracker.service.db.CustomPostgresProfile
import com.teletracker.service.db.model._
import com.teletracker.service.util.Slug
import com.teletracker.service.util.execution.ExecutionContextProvider
import com.twitter.inject.TwitterModule
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.circe.Json
import javax.inject.Inject
import slick.jdbc.{DriverDataSource, JdbcProfile}
import slick.util.AsyncExecutor
import java.util.Properties
import scala.concurrent.ExecutionContext

class DbModule extends TwitterModule {
  @Provides
  @Singleton
  def dataSource(
    config: TeletrackerConfig
  )(implicit executionContext: ExecutionContext
  ): javax.sql.DataSource = {
    val props = new Properties()
    if (config.env != "local") {
//      props.setProperty(
//        "socketFactory",
//        classOf[SocketFactory].getName
//      )
//
//      props.setProperty(
//        "cloudSqlInstance",
//        config.db.cloudSqlInstance.get
//      )

      props.setProperty("ssl", "true")
      props.setProperty("sslmode", "require")
//      props.setProperty("tcpKeepAlive", "true")
    }

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

    println(s"Connecting to DB at: ${config.db.url}")

    new HikariDataSource(conf)
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

class DbImplicits @Inject()(val profile: CustomPostgresProfile) {
  import com.teletracker.service.util.json.circe._
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
  implicit val thingTypeMapper =
    MappedColumnType.base[ThingType, String](_.getName, ThingType.fromString)
  implicit val actionTypeMapper = MappedColumnType
    .base[UserThingTagType, String](_.getName, UserThingTagType.fromString)
  implicit val slugTypeMapper =
    MappedColumnType.base[Slug, String](_.value, Slug.raw)

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
