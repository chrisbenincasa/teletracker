package com.teletracker.common.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.{ConsoleAppender, OutputStreamAppender}

object JsonLogger {
  def register(
    appender: JsonLogger,
    setEncoder: Boolean = false
  ): Unit = {
    if (setEncoder) appender.setEncoder(new EventJsonEncoder)
    Logging.addAppender(appender)
  }
}

trait JsonLogger extends OutputStreamAppender[ILoggingEvent] {
  setEncoder(new EventJsonEncoder)
}

class JsonFileLogger extends RollingFileAppender[ILoggingEvent] with JsonLogger

class JsonConsoleLogger extends ConsoleAppender[ILoggingEvent] with JsonLogger
