package com.teletracker.common.util.time

import scala.concurrent.duration.{Duration, FiniteDuration}
import com.twitter.util.{Duration => TDuration}
import java.util.concurrent.TimeUnit

object DurationConverters {
  implicit def scalaToTwitterConvertible(
    d: FiniteDuration
  ): ScalaDurationToTwitterDuration = new ScalaDurationToTwitterDuration(d)

  implicit def twitterToScalaConvertible(
    d: TDuration
  ): TwitterDurationToScalaDuration = new TwitterDurationToScalaDuration(d)
}

final class ScalaDurationToTwitterDuration(val d: FiniteDuration)
    extends AnyVal {
  def asTwitter: TDuration = TDuration.fromTimeUnit(d.length, d.unit)
}

final class TwitterDurationToScalaDuration(val d: TDuration) extends AnyVal {
  def asScala: FiniteDuration = Duration(d.inMillis, TimeUnit.MILLISECONDS)
}
