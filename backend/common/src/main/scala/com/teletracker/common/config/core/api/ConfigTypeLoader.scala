package com.teletracker.common.config.core.api

import net.ceedubs.ficus.readers.ValueReader
import scala.reflect.ClassTag

/**
  * Config loader interface
  */
trait ConfigTypeLoader {
  def loadType[T <: ConfigWithPath](
    loader: T
  )(implicit valueReader: ValueReader[T#ConfigType],
    tt: ClassTag[T#ConfigType]
  ): ReloadableConfig[T#ConfigType]
}
