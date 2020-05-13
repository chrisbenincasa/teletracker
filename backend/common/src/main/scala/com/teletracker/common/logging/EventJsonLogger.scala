package com.teletracker.common.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.EncoderBase
import com.fasterxml.jackson.databind.SerializationFeature
import io.circe.generic.JsonCodec
import io.circe.syntax._
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.collection.JavaConverters._

object EventJsonEncoder {
  final private val MessageField = "message"
  final private val LevelField = "level"
  final private val TimestampField = "timestamp"
  final private val NameField = "name"
  final private val ThreadNameField = "threadName"

  final private val MdcField = "mdc"
  final private val ArgumentsField = "arguments"

  final private val ApplicationField = "application"
  final private val ApplicationNameField = "name"
  final private val InstanceIdField = "instance_id"
  final private val HostnameField = "hostname"
  final private val EnvironmentField = "environment"
  final private val ContainerIdField = "container_id"

  final private val ExceptionField = "exception"
  final private val ExceptionClassField = "class"
  final private val ExceptionMessageField = "message"
  final private val ExceptionStackField = "stack"

  final private val DefaultMaxStackFrames = 10

  private val hostname = trimToOption(System.getProperty("hostname"))
  private val containerId = trimToOption(System.getProperty("container.id"))

  private def trimToOption(str: String): Option[String] =
    Option(str).flatMap(s => Option(s.trim))
}

class EventJsonEncoder extends EncoderBase[ILoggingEvent] {
  import EventJsonEncoder._

  final private val newlineBytes = System.lineSeparator().getBytes

  private var immediateFlush = true

  private var maxStackFrames = DefaultMaxStackFrames

  override def headerBytes(): Array[Byte] = Array.emptyByteArray

  override def encode(event: ILoggingEvent): Array[Byte] = {
    val jsonMap = createJsonMap(event)

    val bytes = jsonMap.asJson.deepDropNullValues.noSpaces.getBytes()

    if (bytes.nonEmpty) {
      // Append line separator if the serializer hasn't already
      if (bytes.lastIndexOfSlice(newlineBytes) != bytes.length - newlineBytes.length) {
        bytes ++ newlineBytes
      } else {
        bytes
      }
    } else {
      Array.emptyByteArray
    }
  }

  override def footerBytes(): Array[Byte] = Array.emptyByteArray

  protected def createJsonMap(event: ILoggingEvent): LogEvent = {
    val exception = Option(event.getThrowableProxy)
      .map(throwable => {
        LogException(
          `class` = Option(throwable.getClassName),
          message = Option(throwable.getMessage),
          stack = Option(throwable.getStackTraceElementProxyArray)
            .map(_.take(maxStackFrames).map(_.toString))
        )
      })

    LogEvent(
      message = Option(event.getMessage),
      level = Option(event.getLevel.toString),
      timestamp = Option(event.getTimeStamp).map(
        ts => OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC)
      ),
      name = Option(event.getLoggerName),
      thread_name = Option(event.getThreadName),
      container_id = containerId,
      hostname = hostname,
      mdc = Option(event.getMDCPropertyMap)
        .filterNot(_.isEmpty)
        .map(_.asScala.toMap.mapValues(Option(_))),
      exception = exception,
      extras = None
    )
  }

  def setImmediateFlush(immediateFlush: Boolean): Unit = {
    this.immediateFlush = immediateFlush
  }

  def isImmediateFlush: Boolean = immediateFlush

  def getMaxStackFrames: Int = this.maxStackFrames

  def setMaxStackFrames(n: Int): Unit = {
    this.maxStackFrames = n
  }
}

@JsonCodec
case class LogEvent(
  message: Option[String],
  level: Option[String],
  timestamp: Option[OffsetDateTime],
  name: Option[String],
  thread_name: Option[String],
  container_id: Option[String],
  hostname: Option[String],
  mdc: Option[Map[String, Option[String]]],
  exception: Option[LogException],
  extras: Option[Map[String, String]])

@JsonCodec
case class LogException(
  `class`: Option[String],
  message: Option[String],
  stack: Option[Array[String]])
