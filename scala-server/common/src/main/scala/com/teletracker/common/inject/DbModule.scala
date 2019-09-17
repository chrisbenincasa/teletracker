package com.teletracker.common.inject

import com.codahale.metrics.MetricRegistry
import com.google.inject.{Provides, Singleton}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.CustomPostgresProfile
import com.twitter.inject.TwitterModule
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import slick.jdbc.{DriverDataSource, JdbcProfile}
import java.util.Properties
import scala.concurrent.ExecutionContext

class DbModule extends TwitterModule {
  @Provides
  @Singleton
  def dataSource(
    config: TeletrackerConfig,
    metricRegistry: MetricRegistry
  ): javax.sql.DataSource = {
    mkDataSource("DB_Pool", config, metricRegistry)
  }

  protected def mkDataSource(
    poolName: String,
    config: TeletrackerConfig,
    metricRegistry: MetricRegistry
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
    hikari.setMaximumPoolSize(5)
    hikari.setMetricRegistry(metricRegistry)
    hikari
  }
}

class BaseDbModule extends TwitterModule {
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
