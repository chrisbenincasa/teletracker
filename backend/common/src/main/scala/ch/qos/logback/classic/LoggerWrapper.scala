package ch.qos.logback.classic

import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.core.Appender
import ch.qos.logback.core.spi.{
  AppenderAttachable,
  AppenderAttachableImpl,
  FilterReply
}
import org.slf4j.Marker
import org.slf4j.spi.LocationAwareLogger
import scala.collection.JavaConverters._

object LoggerWrapper {
  val FQCN: String = classOf[LoggerWrapper].getName
}

class LoggerWrapper(
  delegate: Logger,
  loggerContext: LoggerContext)
    extends org.slf4j.Logger
    with LocationAwareLogger
    with AppenderAttachable[ILoggingEvent]
    with Serializable {

  import LoggerWrapper._
  private[this] var aai: AppenderAttachableImpl[ILoggingEvent] = _

  override def getName: String = delegate.getName

  override def log(
    marker: Marker,
    fqcn: String,
    levelInt: Int,
    message: String,
    argArray: Array[AnyRef],
    t: Throwable
  ): Unit = {
    val level = Level.fromLocationAwareLoggerInteger(levelInt)
    filterAndLog_0_Or3Plus(fqcn, marker, level, message, argArray, t)
  }

  /**
    * Support SLF4J interception during initialization as introduced in SLF4J version 1.7.15
    *
    * @since 1.1.4
    * @param slf4jEvent
    */
  def log(slf4jEvent: org.slf4j.event.LoggingEvent): Unit = {
    val level = Level.fromLocationAwareLoggerInteger(slf4jEvent.getLevel.toInt)
    filterAndLog_0_Or3Plus(
      FQCN,
      slf4jEvent.getMarker,
      level,
      slf4jEvent.getMessage,
      slf4jEvent.getArgumentArray,
      slf4jEvent.getThrowable
    )
  }

  override def trace(msg: String): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.TRACE, msg, null, null)
  }

  override def trace(
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, null, Level.TRACE, format, arg, null)
  }

  override def trace(
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, null, Level.TRACE, format, arg1, arg2, null)
  }

  override def trace(
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      null,
      Level.TRACE,
      format,
      argArray.toArray,
      null
    )
  }

  override def trace(
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.TRACE, msg, null, t)
  }

  override def trace(
    marker: Marker,
    msg: String
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.TRACE, msg, null, null)
  }

  override def trace(
    marker: Marker,
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, marker, Level.TRACE, format, arg, null)
  }

  override def trace(
    marker: Marker,
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, marker, Level.TRACE, format, arg1, arg2, null)
  }

  override def trace(
    marker: Marker,
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      marker,
      Level.TRACE,
      format,
      argArray.toArray,
      null
    )
  }

  override def trace(
    marker: Marker,
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.TRACE, msg, null, t)
  }

  override def isDebugEnabled: Boolean = isDebugEnabled(null)

  override def isDebugEnabled(marker: Marker): Boolean = {
    val decision = callTurboFilters(marker, Level.DEBUG)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= Level.DEBUG_INT
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  override def debug(msg: String): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.DEBUG, msg, null, null)
  }

  override def debug(
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, null, Level.DEBUG, format, arg, null)
  }

  override def debug(
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, null, Level.DEBUG, format, arg1, arg2, null)
  }

  override def debug(
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      null,
      Level.DEBUG,
      format,
      argArray.toArray,
      null
    )
  }

  override def debug(
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.DEBUG, msg, null, t)
  }

  override def debug(
    marker: Marker,
    msg: String
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.DEBUG, msg, null, null)
  }

  override def debug(
    marker: Marker,
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, marker, Level.DEBUG, format, arg, null)
  }

  override def debug(
    marker: Marker,
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, marker, Level.DEBUG, format, arg1, arg2, null)
  }

  override def debug(
    marker: Marker,
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      marker,
      Level.DEBUG,
      format,
      argArray.toArray,
      null
    )
  }

  override def debug(
    marker: Marker,
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.DEBUG, msg, null, t)
  }

  override def error(msg: String): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.ERROR, msg, null, null)
  }

  override def error(
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, null, Level.ERROR, format, arg, null)
  }

  override def error(
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, null, Level.ERROR, format, arg1, arg2, null)
  }

  override def error(
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      null,
      Level.ERROR,
      format,
      argArray.toArray,
      null
    )
  }

  override def error(
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.ERROR, msg, null, t)
  }

  override def error(
    marker: Marker,
    msg: String
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.ERROR, msg, null, null)
  }

  override def error(
    marker: Marker,
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, marker, Level.ERROR, format, arg, null)
  }

  override def error(
    marker: Marker,
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, marker, Level.ERROR, format, arg1, arg2, null)
  }

  override def error(
    marker: Marker,
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      marker,
      Level.ERROR,
      format,
      argArray.toArray,
      null
    )
  }

  override def error(
    marker: Marker,
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.ERROR, msg, null, t)
  }

  override def isInfoEnabled: Boolean = isInfoEnabled(null)

  override def isInfoEnabled(marker: Marker): Boolean = {
    val decision = callTurboFilters(marker, Level.INFO)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= Level.INFO_INT
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  override def info(msg: String): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.INFO, msg, null, null)
  }

  override def info(
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, null, Level.INFO, format, arg, null)
  }

  override def info(
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, null, Level.INFO, format, arg1, arg2, null)
  }

  override def info(
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      null,
      Level.INFO,
      format,
      argArray.toArray,
      null
    )
  }

  override def info(
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.INFO, msg, null, t)
  }

  override def info(
    marker: Marker,
    msg: String
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.INFO, msg, null, null)
  }

  override def info(
    marker: Marker,
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, marker, Level.INFO, format, arg, null)
  }

  override def info(
    marker: Marker,
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, marker, Level.INFO, format, arg1, arg2, null)
  }

  override def info(
    marker: Marker,
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      marker,
      Level.INFO,
      format,
      argArray.toArray,
      null
    )
  }

  override def info(
    marker: Marker,
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.INFO, msg, null, t)
  }

  override def isTraceEnabled: Boolean = isTraceEnabled(null)

  override def isTraceEnabled(marker: Marker): Boolean = {
    val decision = callTurboFilters(marker, Level.TRACE)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= Level.TRACE_INT
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  override def isErrorEnabled: Boolean = isErrorEnabled(null)

  override def isErrorEnabled(marker: Marker): Boolean = {
    val decision = callTurboFilters(marker, Level.ERROR)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= Level.ERROR_INT
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  override def isWarnEnabled: Boolean = isWarnEnabled(null)

  override def isWarnEnabled(marker: Marker): Boolean = {
    val decision = callTurboFilters(marker, Level.WARN)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= Level.WARN_INT
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  def isEnabledFor(
    marker: Marker,
    level: Level
  ): Boolean = {
    val decision = callTurboFilters(marker, level)
    if (decision eq FilterReply.NEUTRAL)
      delegate.getLevel.levelInt <= level.levelInt
    else if (decision eq FilterReply.DENY) false
    else if (decision eq FilterReply.ACCEPT) true
    else
      throw new IllegalStateException("Unknown FilterReply value: " + decision)
  }

  def isEnabledFor(level: Level): Boolean = isEnabledFor(null, level)

  override def warn(msg: String): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.WARN, msg, null, null)
  }

  override def warn(
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, null, Level.WARN, msg, null, t)
  }

  override def warn(
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, null, Level.WARN, format, arg, null)
  }

  override def warn(
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, null, Level.WARN, format, arg1, arg2, null)
  }

  override def warn(
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      null,
      Level.WARN,
      format,
      argArray.toArray,
      null
    )
  }

  override def warn(
    marker: Marker,
    msg: String
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.WARN, msg, null, null)
  }

  override def warn(
    marker: Marker,
    format: String,
    arg: AnyRef
  ): Unit = {
    filterAndLog_1(FQCN, marker, Level.WARN, format, arg, null)
  }

  override def warn(
    marker: Marker,
    format: String,
    argArray: AnyRef*
  ): Unit = {
    filterAndLog_0_Or3Plus(
      FQCN,
      marker,
      Level.WARN,
      format,
      argArray.toArray,
      null
    )
  }

  override def warn(
    marker: Marker,
    format: String,
    arg1: AnyRef,
    arg2: AnyRef
  ): Unit = {
    filterAndLog_2(FQCN, marker, Level.WARN, format, arg1, arg2, null)
  }

  override def warn(
    marker: Marker,
    msg: String,
    t: Throwable
  ): Unit = {
    filterAndLog_0_Or3Plus(FQCN, marker, Level.WARN, msg, null, t)
  }

  override def addAppender(newAppender: Appender[ILoggingEvent]): Unit =
    delegate.addAppender(newAppender)

  def addWrapperAppender(newAppender: Appender[ILoggingEvent]): Unit = {
    if (aai eq null) {
      aai = new AppenderAttachableImpl[ILoggingEvent]
    }

    aai.addAppender(newAppender)
  }

  override def iteratorForAppenders(
  ): java.util.Iterator[Appender[ILoggingEvent]] = {
    val it = if (aai == null) {
      List.empty[Appender[ILoggingEvent]].iterator.asJava
    } else {
      aai.iteratorForAppenders
    }

    (it.asScala ++ delegate.iteratorForAppenders().asScala).asJava
  }

  override def getAppender(name: String): Appender[ILoggingEvent] = {
    val x = if (aai ne null) {
      Option(aai.getAppender(name))
    } else {
      None
    }

    x.orElse(Option(delegate.getAppender(name))).orNull
  }

  override def isAttached(appender: Appender[ILoggingEvent]): Boolean = {
    val x = if (aai ne null) {
      aai.isAttached(appender)
    } else false

    x || delegate.isAttached(appender)
  }

  override def detachAndStopAllAppenders(): Unit = {
    if (aai ne null) {
      aai.detachAndStopAllAppenders()
    }

    delegate.detachAndStopAllAppenders()
  }

  override def detachAppender(appender: Appender[ILoggingEvent]): Boolean = {
    val x = if (aai ne null) {
      aai.detachAppender(appender)
    } else {
      false
    }

    val s = delegate.detachAppender(appender)

    x || s
  }

  override def detachAppender(name: String): Boolean = {
    val x = if (aai ne null) {
      aai.detachAppender(name)
    } else {
      false
    }

    val s = delegate.detachAppender(name)

    x || s
  }

  private def filterAndLog_0_Or3Plus(
    localFQCN: String,
    marker: Marker,
    level: Level,
    msg: String,
    params: Array[AnyRef],
    t: Throwable
  ): Unit = {
    val decision = loggerContext.getTurboFilterChainDecision_0_3OrMore(
      marker,
      delegate,
      level,
      msg,
      params,
      t
    )
    if (decision eq FilterReply.NEUTRAL)
      if (delegate.getLevel.levelInt > level.levelInt) return
      else if (decision eq FilterReply.DENY) return
    buildLoggingEventAndAppend(localFQCN, marker, level, msg, params, t)
  }

  private def filterAndLog_1(
    localFQCN: String,
    marker: Marker,
    level: Level,
    msg: String,
    param: AnyRef,
    t: Throwable
  ): Unit = {
    val decision = loggerContext.getTurboFilterChainDecision_1(
      marker,
      delegate,
      level,
      msg,
      param,
      t
    )
    if (decision eq FilterReply.NEUTRAL)
      if (delegate.getLevel.levelInt > level.levelInt) return
      else if (decision eq FilterReply.DENY) return
    buildLoggingEventAndAppend(
      localFQCN,
      marker,
      level,
      msg,
      Array[AnyRef](param),
      t
    )
  }

  private def filterAndLog_2(
    localFQCN: String,
    marker: Marker,
    level: Level,
    msg: String,
    param1: AnyRef,
    param2: AnyRef,
    t: Throwable
  ): Unit = {
    val decision = loggerContext.getTurboFilterChainDecision_2(
      marker,
      delegate,
      level,
      msg,
      param1,
      param2,
      t
    )
    if (decision eq FilterReply.NEUTRAL) {
      if (delegate.getLevel.levelInt > level.levelInt) {
        return
      }
    } else if (decision eq FilterReply.DENY) {
      return
    }
    buildLoggingEventAndAppend(
      localFQCN,
      marker,
      level,
      msg,
      Array[AnyRef](param1, param2),
      t
    )
  }

  private def buildLoggingEventAndAppend(
    localFQCN: String,
    marker: Marker,
    level: Level,
    msg: String,
    params: Array[AnyRef],
    t: Throwable
  ): Unit = {
    val le = new LoggingEvent(localFQCN, delegate, level, msg, t, params)
    le.setMarker(marker)
    callAppenders(le)
  }

  def callAppenders(event: ILoggingEvent): Unit = {
    appendLoopOnAppenders(event)
  }

  private def appendLoopOnAppenders(event: ILoggingEvent) = {
    val res = if (aai != null) {
      aai.appendLoopOnAppenders(event)
    } else {
      0
    }

    delegate.callAppenders(event)

    res
  }

  private def callTurboFilters(
    marker: Marker,
    level: Level
  ) =
    loggerContext.getTurboFilterChainDecision_0_3OrMore(
      marker,
      delegate,
      level,
      null,
      null,
      null
    )
}
