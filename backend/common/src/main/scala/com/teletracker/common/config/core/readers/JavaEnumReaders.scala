package com.teletracker.common.config.core.readers

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.BadValue
import net.ceedubs.ficus.readers.ValueReader
import scala.reflect.ClassTag

trait JavaEnumReaders {
  implicit def mapKeyValueReader[T <: Enum[_]: ClassTag, V](
    implicit valueReader: ValueReader[V]
  ): ValueReader[Map[T, V]] = {
    ValueReaders
      .mapValueReader[V]
      .map(_.map {
        case (k, v) =>
          javaEnumValueFromString[T](k)
            .getOrElse(throw new IllegalArgumentException) -> v
      })
  }

  implicit def javaEnumerationValueReader[
    T <: Enum[_]: ClassTag
  ]: ValueReader[T] = new ValueReader[T] {
    override def read(
      config: Config,
      path: String
    ): T = {
      val value = config.getString(path)
      javaEnumValueFromString[T](value).getOrElse {
        throw new BadValue(
          config.origin(),
          path,
          s"${value} isn't a valid value for enum: ${runtimeClass[T].getCanonicalName}; " +
            s"allowed values: ${getJavaEnumFields[T].mkString(", ")}"
        )
      }
    }
  }

  private def runtimeClass[T: ClassTag] = implicitly[ClassTag[T]].runtimeClass

  private def getJavaEnumFields[T <: Enum[_]: ClassTag] = {
    val rt = runtimeClass[T]
    rt.getFields
      .filter(_.isEnumConstant)
      .map(f => rt.getField(f.getName).get(null).asInstanceOf[T])
  }

  private def javaEnumValueFromString[T <: Enum[_]: ClassTag](
    str: String
  ): Option[T] = {
    getJavaEnumFields[T].find(_.toString.equalsIgnoreCase(str))
  }
}
