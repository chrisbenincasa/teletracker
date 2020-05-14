package com.teletracker.common.logging

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger, LoggerContext, LoggerWrapper}
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.{
  RollingFileAppender,
  SizeAndTimeBasedFNATP,
  TimeBasedRollingPolicy
}
import ch.qos.logback.core.status.OnConsoleStatusListener
import ch.qos.logback.core.util.FileSize
import org.slf4j
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

object TaskLogger {
  final private val LogFormat =
    "%d [%X{traceId}] [%thread] %-5level %logger{36} %marker - %msg%n"

  def make(
    clazz: Class[_],
    s3Client: S3Client,
    s3Bucket: String,
    s3KeyPrefix: String,
    outputToConsole: Boolean
  ): (slf4j.Logger, () => Unit) = synchronized {
    val now = System.currentTimeMillis()
    val factory = LoggerFactory.getILoggerFactory

    factory match {
      case context: LoggerContext =>
        val s3Appender =
          getS3Logger(context, clazz, now, s3Bucket, s3KeyPrefix, s3Client)

        val jsonEncoder = new EventJsonEncoder
        jsonEncoder.start()

        val consoleAppender = new ConsoleAppender[ILoggingEvent]
        consoleAppender.setContext(context)
        consoleAppender.setEncoder(jsonEncoder)
        consoleAppender.setName(clazz.getName)

        val finalLogger = {
          val logger = factory
            .getLogger(clazz.getName)
            .asInstanceOf[Logger]

          val wrapper = new LoggerWrapper(logger, context)

          wrapper.addWrapperAppender(s3Appender)

          if (outputToConsole && (logger
                .getAppender(consoleAppender.getName) eq null)) {
            consoleAppender.start()
            logger.addAppender(consoleAppender)
          }

          logger.setLevel(Level.INFO)
          logger.setAdditive(false)

          wrapper
        }

        finalLogger -> (() => {
          finalLogger.detachAndStopAllAppenders()
        })

      case _ => factory.getLogger(clazz.getName) -> (() => Unit)
    }
  }

  private def getS3Logger(
    context: LoggerContext,
    clazz: Class[_],
    now: Long,
    s3Bucket: String,
    s3KeyPrefix: String,
    s3Client: S3Client
  ) = {
    val encoder = new PatternLayoutEncoder()
    encoder.setPattern(LogFormat)
    encoder.setContext(context)
    encoder.start()

    val s3Appender = new RollingFileAppender[ILoggingEvent]()
    s3Appender.setFile(s"${clazz.getSimpleName}-$now.log")
    s3Appender.setEncoder(encoder)
    s3Appender.setImmediateFlush(true)
    s3Appender.setContext(context)

    val rollingPolicy = new S3RollingPolicy
    rollingPolicy.setContext(context)
    rollingPolicy.setBucketName(s3Bucket)
    rollingPolicy.setKeyPrefix(s3KeyPrefix)
    rollingPolicy.setS3Client(s3Client)
    rollingPolicy.setFileNamePattern(
      s"${clazz.getSimpleName}-$now-%d{yyyy-MM-dd}.%i"
    )
    rollingPolicy.setParent(s3Appender)
    rollingPolicy.start()

    val triggeringPolicy = new SizeAndTimeBasedFNATP[ILoggingEvent]()
    val tbrp = new TimeBasedRollingPolicy[ILoggingEvent]
    tbrp.setContext(context)
    tbrp.setFileNamePattern(s"${clazz.getSimpleName}-$now-%d.%i")
    tbrp.setParent(s3Appender)
    tbrp.start()

    triggeringPolicy.setTimeBasedRollingPolicy(tbrp)
    triggeringPolicy.setMaxFileSize(
      new FileSize(50 * FileSize.MB_COEFFICIENT)
    )
    triggeringPolicy.setContext(context)
    triggeringPolicy.start()

    s3Appender.setTriggeringPolicy(triggeringPolicy)
    s3Appender.setRollingPolicy(rollingPolicy)
    s3Appender.start()

    s3Appender
  }
}
