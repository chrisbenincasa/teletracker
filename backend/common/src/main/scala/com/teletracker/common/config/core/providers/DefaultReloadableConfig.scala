package com.teletracker.common.config.core.providers

import com.teletracker.common.config.core.api.{
  InvalidConfigurationException,
  ReloadableConfig,
  Verified,
  WatchToken
}
import com.teletracker.common.config.core.scheduler.DaemonThreadFactory
import com.typesafe.config.Config
import java.util.concurrent.{Executors, TimeUnit}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import scala.concurrent.duration.{Duration, _}

class InvalidConfigurationObject(
  msg: String,
  root: Exception)
    extends Exception(msg, root)
    with InvalidConfigurationException

/**
  * A config object that lets you register watchers
  *
  * @param reloadable
  * @param namespace
  * @param valueReader
  * @tparam T
  */
class DefaultReloadableConfig[T](
  reloadable: ThreadSafeUpdatable[Config],
  namespace: String
)(implicit valueReader: ValueReader[T])
    extends ReloadableConfig[T]
    with ThreadSafeSubscriber[Config] {
  protected val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private val scheduler = Executors.newSingleThreadScheduledExecutor(
    new DaemonThreadFactory(namespace + "-reloadable")
  )

  private var _lastGood: Option[T] = None

  // seed the cache
  onChange(reloadable.currentValue())

  // register callbacks to re-cache when the underlying config changes
  reloadable.register(this)

  override def onChange(data: Config): Unit = {
    synchronized {
      try {
        val config = data.as[T](namespace)

        // if the config is an instance of verified
        // verify it before it becomes loaded.
        if (classOf[Verified].isAssignableFrom(config.getClass)) {
          config.asInstanceOf[Verified].verify()
        }

        lastGood = config

        config
      } catch {
        case e: InvalidConfigurationException =>
          throw e
        case ex: Exception =>
          logger.warn(
            s"Current config value is not serializable as an instance of the configuration! Returning last known good",
            ex
          )

          lastGood.getOrElse(
            throw new InvalidConfigurationObject(
              "The current configuration is invalid and there has been no last known good!",
              ex
            )
          )
      }
    }
  }

  private def lastGood_=(t: T): Unit = {
    synchronized {
      _lastGood = Some(t)
    }
  }

  private def lastGood: Option[T] = {
    synchronized {
      _lastGood
    }
  }

  /**
    * The current view of the configuration given the default state + overrides
    *
    * @return
    */
  def currentValue(): T = {
    lastGood.get
  }

  /**
    * Watch for changes asynchronously at the interval specified
    *
    * Do not block on the invoked watcher thread or you will block future updates
    *
    * @param duration
    * @param onChange
    * @return
    */
  def watch(duration: Duration = 1 second)(onChange: T => Unit): WatchToken = {
    val future = scheduler.scheduleWithFixedDelay(
      new Runnable {
        @volatile var lastSeenValue = currentValue()

        override def run(): Unit = {
          val nextValue = currentValue()
          if (nextValue != lastSeenValue) {
            lastSeenValue = nextValue
            onChange(nextValue)
          }
        }
      },
      0,
      duration.toMillis,
      TimeUnit.MILLISECONDS
    )

    new WatchToken(Some(future))
  }
}
