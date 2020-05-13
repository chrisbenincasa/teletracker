package com.teletracker.common.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import org.scalatest.OptionValues
import java.io.ByteArrayOutputStream
import org.scalatest.flatspec.AnyFlatSpec
import org.slf4j.MDC
import pprint.PPrinter

class JsonLoggerTests extends AnyFlatSpec with OptionValues {
  Logging.setLogLevel(Level.INFO)

  private val pprint = PPrinter.BlackWhite

  private val appender = {
    val a = new JsonOutputStreamAppender
    val os = new ByteArrayOutputStream()
    a.setOutputStream(os) // Have to give it an initial output stream or tests wont work
    JsonLogger.register(a)
    a
  }

  "JsonLogger" should "log" in withFreshStream { implicit os =>
    val threadName = Thread.currentThread().getName
    val logger = freshLogger

    val logs = logAndFlush {
      logger.info("hey hey")
    }

    val log = logs.head

    assert(log.name.value === getClass.getName)
    assert(log.message.value === "hey hey")
    assert(log.thread_name.value === threadName)
    assert(log.level.value === "INFO")
  }

  it should "log errors" in withFreshStream { implicit os =>
    val t = new Throwable("bad things")

    val logger = freshLogger

    val logs = logAndFlush {
      logger.error("Bad things happening", t)
    }

    val log = logs.head

    assert(log.level contains "ERROR")
    assert(log.exception.isDefined)

    val exception = log.exception.get

    assert(exception.`class` contains t.getClass.getName)
    assert(exception.stack.get.nonEmpty)
  }

  it should "include MDC context" in withFreshStream { implicit os =>
    MDC.put("christian", "rules")
    MDC.put("clientId", 123.toString)

    val logger = freshLogger

    val logs = logAndFlush {
      logger.info("gimme the logs")
    }

    val log = logs.head

    assert(log.mdc.isDefined)

    val mdc = log.mdc.get

    assert(mdc("christian").value === "rules")
    assert(mdc("clientId").value === "123")

    MDC.clear()
  }

  private def freshLogger = org.slf4j.LoggerFactory.getLogger(getClass)

  private def logAndFlush(
    f: => Unit
  )(implicit os: ByteArrayOutputStream
  ): Array[LogEvent] = {
    f
    val flushed = flushLogs(os)
    pprint.pprintln(flushed)
    flushed
  }

  private def withFreshStream(code: ByteArrayOutputStream => Any) = {
    val os = new ByteArrayOutputStream()
    try {
      appender.setOutputStream(os)
      code(os)
    } finally {
      os.flush()
      os.close()
    }
  }

  private def flushLogs(os: ByteArrayOutputStream): Array[LogEvent] = {
    val str = new String(os.toByteArray)

    val log = str.split("\n").filter(_.nonEmpty)

    log.map(io.circe.parser.decode[LogEvent](_).fold(e => throw e, identity))
  }
}

class JsonOutputStreamAppender
    extends OutputStreamAppender[ILoggingEvent]
    with JsonLogger
