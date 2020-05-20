package com.teletracker.common.config.core.meta

import com.teletracker.common.config.core.ConfigLoader
import com.teletracker.common.config.core.api.ConfigWithPath
import com.teletracker.common.config.core.sources.{
  AppName,
  Source,
  SourceFactory
}
import com.typesafe.config.Config
import java.io.File
import scala.concurrent.duration.{Duration, _}

case class LoadableFileConfig(suffix: String = "conf")

object MetaConfiguration extends ConfigWithPath {
  override type ConfigType = MetaConfiguration

  override val path = ConfigLoader.META_NAMESPACE

  final val DEFAULT_OVERRIDE_DIR = new File("/opt/teletracker/config-overrides")
  final val DEFAULT_RELOAD_INTERVAL = 1 second
}

/**
  * The meta configuration object. This is responsible for controlling how remote configurations are loaded
  *
  * @param override_file_dir             The location of the override directory
  * @param override_file_reload_interval How often to reload the stored override file
  * @param remote                        Optional remote configuration to be parsed by the source factory
  */
case class MetaConfiguration(
  override_file_dir: File,
  override_file_reload_interval: Duration,
  remote: Option[Config]) {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  /**
    * How often to refresh the remote source
    *
    * @return
    */
  def remoteRefreshDuration: Option[Duration] =
    remote.map(r => Duration(r.getString("reload_interval")))

  /**
    * How long to wait for the remote source before bailing
    *
    * @return
    */
  def getInitialLoadWait: Option[Duration] =
    remote.map(r => Duration(r.getString("initial_load_wait")))

  def enabled = remote.exists(_.getBoolean("enabled"))

  /**
    * Load a source factory from the remote meta configuration
    *
    * @return
    */
  def getSourceFactory: Option[AppName => Source] = {
    try {
      if (!enabled) {
        logger.info("Remote config loading is not enabled")

        return None
      }

      val r = remote.get

      val clazz = r.getString("class").trimToOption

      if (clazz.isEmpty) {
        logger.info(
          "Remote config loading is not enabled because source factory class is empty"
        )

        return None
      }

      val constructors = Class.forName(clazz.get).getConstructors

      val zeroArg = constructors
        .find(c => c.getParameterCount == 0)
        .map(_.newInstance().asInstanceOf[SourceFactory])

      val factory =
        zeroArg.getOrElse(
          throw new RuntimeException(
            s"Class ${clazz} does not have a zero arg constructor"
          )
        )

      Some((appName) => factory.source(remote.get, appName))
    } catch {
      case ex: Throwable =>
        logger.warn(
          "Unable to create source factory! Not enabling remote config loading",
          ex
        )

        None
    }
  }

  def overrideFilePath(
    namespace: AppName,
    confConfig: LoadableFileConfig = LoadableFileConfig()
  ): File = {
    val separator =
      if (override_file_dir.getAbsolutePath.endsWith("/")) "" else "/"

    new File(
      s"${override_file_dir}${separator}${namespace.value}.${confConfig.suffix}"
    )
  }

  implicit private class RichString(str: String) {
    def trimToOption: Option[String] = {
      Option(str).map(_.trim).flatMap(s => if (s.length == 0) None else Some(s))
    }
  }
}
