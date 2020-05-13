package com.teletracker.common.logging

import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.Appender
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

object Logging {
  def context = {
    val factory = LoggerFactory.getILoggerFactory

    if (factory.isInstanceOf[LoggerContext]) {
      LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    } else {
      // we got into a weird state,
      // try our best to continue forward
      new LoggerContext
    }
  }

//  def configuration = {
//    val delegate = new ConfigurationDelegate
//    delegate.setContext(context)
//    delegate
//  }

  def rootLogger: Logger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

  private lazy val mutatedLoggerLevels = new mutable.HashMap[String, Level]()

  def addAppender(appenders: Appender[ILoggingEvent]*): Unit = {
    addAppender(rootLogger, appenders: _*)
  }

  def addAppender(
    logger: Logger,
    appenders: Appender[ILoggingEvent]*
  ): Unit = {
    appenders.foreach { appender =>
      appender.setContext(context)
      appender.start()
      logger.addAppender(appender)
    }
  }

  def setAppender(
    logger: Logger,
    appenders: Appender[ILoggingEvent]*
  ): Unit = {
    logger.setAdditive(false)

    appenders.foreach { appender =>
      appender.setContext(context)
      appender.start()
      logger.iteratorForAppenders.asScala
        .foreach(a => logger.detachAppender(a.getName))
      logger.addAppender(appender)
    }
  }

  private def appenders = rootLogger.iteratorForAppenders().asScala

  /**
    * Sets the log level for all appenders
    *
    * @param level
    */
  def setLogLevel(level: Level) = {
    rootLogger.setLevel(level)
  }

  /**
    * Sets the log level for a particular appender
    *
    * @param value
    * @param appenderName
    */
  def setAppenderLevel(
    value: Level,
    appenderName: String
  ) = {
    appenders.find(_.getName == appenderName) match {
      case Some(appender) =>
        val filters = appender.getCopyOfAttachedFiltersList.asScala

        filters
          .find(_.isInstanceOf[ThresholdFilter])
          .foreach(
            m => m.asInstanceOf[ThresholdFilter].setLevel(value.toString)
          )
      case None =>
    }
  }

//  /**
//    * Sets the log level for the fully qualified class name or namespaces. Wildcards allowed
//    *
//    * @param value
//    * @param packageNames
//    */
//  def setClassNameLevel(
//    value: Level,
//    packageNames: List[String]
//  ) = {
//    synchronized {
//      val packagesNotSet = packageNames.toSet -- mutatedLoggerLevels.keySet
//
//      mutatedLoggerLevels ++= packagesNotSet.map(m => m -> getLoggerLevel(m))
//    }
//
//    packageNames.foreach(c => configuration.logger(c, value))
//  }
//
//  /**
//    * Reset a class level back to its original
//    *
//    * @param packageName the package name
//    * @return
//    */
//  def resetClassLevel(packageName: String) = {
//    synchronized {
//      mutatedLoggerLevels
//        .get(packageName)
//        .foreach(setClassNameLevel(_, List(packageName)))
//
//      mutatedLoggerLevels -= packageName
//    }
//  }
//
//  /**
//    * Resets all mutated class levels back to their default
//    */
//  def resetClassLevels(): Unit = {
//    synchronized {
//      mutatedLoggerLevels.foreach {
//        case (name, level) => setClassNameLevel(level, List(name))
//      }
//
//      mutatedLoggerLevels.clear()
//    }
//  }
//
//  private def getLoggerLevel(className: String): Level = {
//    Option(context.getLogger(className).getLevel)
//      .map(m => Try(Level.valueOf(m.levelStr)).getOrElse(Level.INFO))
//      .getOrElse(Level.INFO)
//  }
//
//  /**
//    * Sets the log level for the classes
//    *
//    * @param value
//    * @param classes
//    */
//  def setClassTypeLevel(
//    value: Level,
//    classes: List[Class[_]]
//  ) = {
//    setClassNameLevel(value, classes.map(_.getName))
//  }
}
