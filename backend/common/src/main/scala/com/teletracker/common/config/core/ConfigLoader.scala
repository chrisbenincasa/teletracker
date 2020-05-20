package com.teletracker.common.config.core

import com.teletracker.common.config.core.api.{
  ConfigTypeLoader,
  ConfigWithPath,
  ReloadableConfig,
  Verified
}
import com.teletracker.common.config.core.meta.{
  LoadableFileConfig,
  MetaConfiguration
}
import com.teletracker.common.config.core.providers.{
  DefaultReloadableConfig,
  ThreadSafeUpdatable
}
import com.teletracker.common.config.core.scheduler.DaemonThreadFactory
import com.teletracker.common.config.core.sources.{
  AppName,
  Source,
  SourceWriter
}
import com.teletracker.common.util.{Environment, Location, Realm}
import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import java.util.concurrent._
import com.teletracker.common.config.core.readers.ValueReaders._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import org.apache.commons.io.FileUtils
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object ConfigLoader {
  val META_NAMESPACE = "config_override"

  /**
    * Verify a configuration without loading any remote state
    *
    * @param loader
    * @param realm
    * @param valueReader
    * @param tt
    * @tparam T
    * @return
    */
  def verify[T <: ConfigWithPath](
    loader: T,
    realm: Realm = Environment.realm,
    resourceRootPath: String = "",
    extraResourcesToLoad: List[String] = Nil
  )(implicit valueReader: ValueReader[T#ConfigType],
    tt: ClassTag[T#ConfigType],
    executionContext: ExecutionContext
  ): scala.util.Try[Unit] = {
    verifyEnv[T](
      loader,
      Environment.load.withRealm(realm),
      resourceRootPath,
      extraResourcesToLoad
    )
  }

  /**
    * Verify a configuration without loading any remote state
    *
    * @param loader
    * @param environment
    * @param valueReader
    * @param tt
    * @tparam T
    * @return
    */
  def verifyEnv[T <: ConfigWithPath](
    loader: T,
    environment: Environment,
    resourceRootPath: String = "",
    extraResourcesToLoad: List[String]
  )(implicit valueReader: ValueReader[T#ConfigType],
    tt: ClassTag[T#ConfigType],
    executionContext: ExecutionContext
  ): scala.util.Try[Unit] = {
    scala.util.Try(
      new StaticLoader(environment, resourceRootPath, extraResourcesToLoad)
        .loadType(loader)
    )
  }
}

/**
  * Loads a configuration from the namespace in the config file
  *
  * Create one config loader PER application.  It can be re-used to load configurations
  *
  * Creating more than one instance of this can cause you to reload other application's configurations
  * from remote sources, so beware!
  *
  * @param appName              The application name. This corresponds (usually) to a location on the remote system as well as which override file is created
  * @param resourceRootPath     Optional to specify a subfolder to load from in the resources directory
  * @param extraResourcesToLoad Optional extra list of configurations relative to the root resources directory
  */
class ConfigLoader(
  appName: AppName,
  resourceRootPath: String = "",
  environment: Environment = Environment.load,
  extraResourcesToLoad: List[String] = Nil
)(implicit executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global)
    extends ConfigTypeLoader {
  @deprecated(
    message =
      "Please use the constructor that includes the full environment loader",
    since = "2017.10.18"
  )
  def this(
    appName: AppName,
    resourceRootPath: String,
    realm: Realm,
    extraResourcesToLoad: List[String]
  ) {
    this(
      appName,
      resourceRootPath,
      Environment.load.withRealm(realm),
      extraResourcesToLoad
    )
  }

  private lazy val mirror = runtimeMirror(getClass.getClassLoader)

  private var watchedConfiguration: Option[ScheduledFuture[_]] = None

  private var watchedRemoteFile: Option[ScheduledFuture[_]] = None

  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  protected val enableRemoteAndWatchers = true

  /**
    * What is the suffix of files we will load
    */
  protected val confConfig: LoadableFileConfig = LoadableFileConfig()

  def getWatchedConfiguration: Option[ScheduledFuture[_]] = watchedConfiguration

  /**
    * Convert a profile to the expected configuration base name. i.e. <profile>.<suffix>
    */
  implicit private class RichProfile(environment: Environment) {
    def envWithLocationName: String = environment match {
      case Environment(env, location) => {
        s"${profileToName(env)}.${locationToName(location)}"
      }
    }

    def envToFileName: String = environment match {
      case Environment(env, _) => profileToName(env)
    }

    def locationToFileName: String = environment match {
      case Environment(_, location) => locationToName(location)
    }

    private def profileToName(realm: Realm) = realm match {
      case Realm.Qa   => "qa"
      case Realm.Prod => "prod"
    }

    private def locationToName(location: Location) = location match {
      case Location.Local  => "local"
      case Location.Remote => "remote"
    }
  }

  /**
    * The scheduler to kick off to query the remote sources
    *
    * The scheduler runs in the background to not block the foreground process
    */
  private val scheduler = Executors.newScheduledThreadPool(
    1,
    new DaemonThreadFactory(appName.value + "-config")
  )

  /**
    * Watch the folder this relates to
    *
    * @param file
    * @param onChange
    * @return
    */
  private def watchFile(
    metaConfiguration: MetaConfiguration,
    file: File
  )(
    onChange: => Unit
  ): Unit = {
    if (!enableRemoteAndWatchers) {
      return
    }

    watchedRemoteFile = Some(
      scheduler.scheduleWithFixedDelay(
        new Runnable {
          @volatile var lastModified = file.lastModified()
          @volatile var exists = file.exists()

          override def run() = {
            if (file.lastModified() > lastModified || file.exists() != exists) {
              lastModified = file.lastModified()
              exists = file.exists()

              logger.info(s"${file.getAbsolutePath} modified, reloading config")

              onChange
            }
          }
        },
        metaConfiguration.override_file_reload_interval.toMillis,
        metaConfiguration.override_file_reload_interval.toMillis,
        TimeUnit.MILLISECONDS
      )
    )
  }

  def close(): Unit = {
    watchedConfiguration.map(_.cancel(true))
    watchedRemoteFile.map(_.cancel(true))
  }

  /**
    * Deferred initialize of creating a single mungable config
    * that you can get parsers off of
    */
  lazy val currentConfigContainer: ThreadSafeUpdatable[Config] = {
    val metaConfig = loadMetaConfig()

    val overrideFile = metaConfig.overrideFilePath(appName, confConfig)

    val configMerger = getConfigMerger(metaConfig, overrideFile)

    // get the initial config
    val currentConfigContainer = new ThreadSafeUpdatable(configMerger.get)

    watchChanges(metaConfig, overrideFile, currentConfigContainer, configMerger)

    // reload the config now we've seeded remote changes
    currentConfigContainer.update(configMerger.get)

    currentConfigContainer
  }

  /**
    * Load a namespace out of the hocon configuration.
    *
    * @param namespace
    * @param valueReader
    * @tparam T
    * @return
    */
  def load[T](
    namespace: String
  )(implicit valueReader: ValueReader[T],
    tt: ClassTag[T]
  ): ReloadableConfig[T] = {
    verifySystemPreReqs[T]

    new DefaultReloadableConfig[T](currentConfigContainer, namespace)
  }

  /**
    * Get a reference to the current underlying configuration
    *
    * @return
    */
  def currentConfig(): Config = currentConfigContainer.currentValue()

  /**
    * Load a typed config
    */
  override def loadType[T <: ConfigWithPath](
    loader: T
  )(implicit valueReader: ValueReader[T#ConfigType],
    tt: ClassTag[T#ConfigType]
  ): ReloadableConfig[T#ConfigType] = {
    load[T#ConfigType](loader.path)
  }

  private def verifySystemPreReqs[T](implicit ct: ClassTag[T]): Unit = {
    val module = try {
      val moduleSymbol = mirror.staticModule(ct.runtimeClass.getName)
      Some(mirror.reflectModule(moduleSymbol))
    } catch {
      case NonFatal(e) => {
        logger.debug("Unable to load companion module. This is likely fine.", e)
        None
      }
    }

    module.foreach(_.instance match {
      case verifiable: Verified => verifiable.verify()
      case _                    =>
    })
  }

  private def watchChanges[T](
    metaConfig: MetaConfiguration,
    overrideFile: File,
    threadSafeUpdatable: ThreadSafeUpdatable[Config],
    configFactory: ConfigMerger
  ): Unit = {

    if (!enableRemoteAndWatchers) {
      return
    }

    logger.info(
      s"Config overrides available at ${overrideFile.getAbsolutePath}"
    )

    // make sure the override directory exists
    try {
      FileUtils.forceMkdirParent(overrideFile)
    } catch {
      case ex: Exception =>
        logger.warn(
          s"Unable to create overrides folder at ${overrideFile.getParent}!",
          ex
        )
    }

    if (watchedConfiguration.isEmpty) {
      // watch the remote source for changes
      metaConfig.getSourceFactory.foreach(factory => {
        val source = factory(appName)

        watchRemoteSource(metaConfig, source, configFactory)
      })
    }

    // watch the application override file for changes
    watchFile(metaConfig, metaConfig.overrideFilePath(appName)) {
      if (configFactory.verifyCurrent()) {
        threadSafeUpdatable.update(configFactory.get)
      } else {
        logger.warn(
          "Current config file is invalid!! Not updating current object"
        )
      }
    }
  }

  /**
    * Create the config object that will be reloaded
    */
  private def getConfigMerger[T](
    metaConfig: MetaConfiguration,
    overrideFile: File
  ): ConfigMerger = {
    val extraConfigs = if (extraResourcesToLoad.nonEmpty) {
      Some(
        extraResourcesToLoad
          .map(ConfigFactory.parseResources)
          .reduce(_ withFallback _)
      )
    } else {
      None
    }

    new ConfigMerger(
      overrideFile,
      preConfigs = extraConfigs.toList,
      postConfigs = heirarchalEnvironmentFiles
    )
  }

  private lazy val heirarchalEnvironmentFiles: List[Config] = {
    List(
      environment.envWithLocationName,
      environment.envToFileName,
      s"application.${environment.locationToFileName}",
      "application"
    ).map(formatFilename(_)).map(ConfigFactory.parseResources)
  }

  /**
    * Query the remote source for configuration on an interval. Dump the remote data to disk
    *
    * @param metaConfiguration
    * @param source
    */
  private def watchRemoteSource(
    metaConfiguration: MetaConfiguration,
    source: Source,
    configProvider: ConfigMerger
  ): Unit = {
    if (watchedConfiguration.isDefined) {
      logger.warn(
        "Watched configuration is already defined! Not watching again"
      )

      return
    }

    val interval = metaConfiguration.remoteRefreshDuration.getOrElse(60 seconds)

    val writeTo = metaConfiguration.overrideFilePath(source.appName, confConfig)

    val remoteProvider = new Runnable {
      override def run(): Unit = {
        val readerWriter = new SourceWriter(source, writeTo)

        val newConfigBlob = readerWriter.read()

        newConfigBlob.foreach(config => {
          if (configProvider.verifyNext(config)) {
            readerWriter.writeContents(config)
          } else {
            logger.warn(
              "Remote config file did not pass verification!! Not writing to disk"
            )
          }
        })
      }
    }

    // do an initial pull from the remote source
    logger.info(s"Loading from remote source ${source.description}")

    try {
      Await.result(Future {
        remoteProvider.run()
      }, metaConfiguration.getInitialLoadWait.getOrElse(5 seconds))
    } catch {
      case ex: Exception =>
        logger.warn(
          "Unable to load from the remote provider in a timely fashion!"
        )
    }

    // schedule the remote to run at the remote interval
    watchedConfiguration = Some(
      scheduler.scheduleWithFixedDelay(
        remoteProvider,
        interval.toMillis,
        interval.toMillis,
        TimeUnit.MILLISECONDS
      )
    )
  }

  private def loadMetaConfig(): MetaConfiguration = {
    val metaRootConfig = heirarchalEnvironmentFiles.reduce(_ withFallback _)

    scala.util.Try {
      ConfigFactory
        .defaultOverrides()
        .withFallback(metaRootConfig)
        .withOnlyPath(ConfigLoader.META_NAMESPACE)
        .as[MetaConfiguration](ConfigLoader.META_NAMESPACE)
    } match {
      case Failure(exception) =>
        logger.warn(
          "Failed to load meta config. Falling back to default.",
          exception
        )
        MetaConfiguration(
          MetaConfiguration.DEFAULT_OVERRIDE_DIR,
          MetaConfiguration.DEFAULT_RELOAD_INTERVAL,
          remote = None
        )
      case Success(value) => value
    }
  }

  protected def formatFilename(
    base: String,
    root: String = resourceRootPath
  ) = s"${root}${base}.${confConfig.suffix}"

  override def finalize(): Unit = {
    // shut down the async watchers if this object gets GC'd
    try {
      scheduler.shutdownNow()
    } catch {
      case e: Throwable => {}
    }

    super.finalize()
  }
}

class ConfigMerger(
  overrideFile: File,
  preConfigs: List[Config],
  postConfigs: List[Config]) {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def get: Config = {
    val overrideConfig = ConfigFactory.parseFile(overrideFile)

    buildWith(overrideConfig)
  }

  private def buildWith(overrideConfig: Config): Config = {
    val configs =
      if (preConfigs.nonEmpty) {
        preConfigs
          .reduce(_ withFallback _)
          .withFallback(overrideConfig)
          .withFallback(postConfigs.reduce(_ withFallback _))
      } else {
        overrideConfig.withFallback(postConfigs.reduce(_ withFallback _))
      }

    ConfigFactory.defaultOverrides().withFallback(configs).resolve
  }

  def verifyCurrent(): Boolean = {
    verify(get)
  }

  def verifyNext(newOverrides: String): Boolean = {
    verify(buildWith(ConfigFactory.parseString(newOverrides)))
  }

  private def verify(config: => Config) = {
    try {
      config.isResolved

      true
    } catch {
      case ex: Exception =>
        logger.warn("New override config blob is invalid!", ex)

        false
    }
  }
}
