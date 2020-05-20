package com.teletracker.common.config.core

import com.teletracker.common.config.core.api.ConfigWithPath
import com.teletracker.common.config.core.sources.AppName
import com.teletracker.common.util.{Environment, Realm}
import net.ceedubs.ficus.readers.ValueReader
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

/**
  * A static loader that loads the path without access to remote overrides or reloadable configs
  *
  * @param environment
  * @param resourceRootPath
  * @param extraResourcesToLoad
  * @param executionContext
  */
class StaticLoader(
  environment: Environment = Environment.load,
  resourceRootPath: String = "",
  extraResourcesToLoad: List[String] = Nil
)(implicit executionContext: ExecutionContext) {

  def this(
    realm: Realm,
    resourceRootPath: String
  )(implicit executionContext: ExecutionContext
  ) = {
    this(Environment.load.withRealm(realm), resourceRootPath)
  }

  def this(environment: Realm)(implicit executionContext: ExecutionContext) = {
    this(environment, resourceRootPath = "")
  }

  lazy val innerLoader = new ConfigLoader(
    AppName("static"),
    resourceRootPath,
    environment,
    extraResourcesToLoad
  ) {
    override protected val enableRemoteAndWatchers = false
  }

  def load[T](
    path: String
  )(implicit valueReader: ValueReader[T],
    tt: ClassTag[T]
  ): T = {
    innerLoader.load(path).currentValue()
  }

  def loadType[T <: ConfigWithPath](
    loader: T
  )(implicit valueReader: ValueReader[T#ConfigType],
    tt: ClassTag[T#ConfigType]
  ): T#ConfigType = {
    innerLoader.loadType(loader).currentValue()
  }
}
