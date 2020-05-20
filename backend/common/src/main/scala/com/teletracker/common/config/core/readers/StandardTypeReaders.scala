package com.teletracker.common.config.core.readers

import com.typesafe.config.Config
import java.io.File
import java.net.{URI, URL}
import net.ceedubs.ficus.readers.ValueReader
import scala.concurrent.duration.Duration
import scala.util.matching.Regex

trait StandardTypeReaders {
  implicit val url = new ValueReader[URL] {
    override def read(
      config: Config,
      path: String
    ): URL = {
      new URL(config.getString(path))
    }
  }

  implicit val uri = new ValueReader[URI] {
    override def read(
      config: Config,
      path: String
    ): URI = {
      new URI(config.getString(path))
    }
  }

  implicit val optionConfig = new ValueReader[Option[Config]] {
    override def read(
      config: Config,
      path: String
    ): Option[Config] = {
      if (config.hasPath(path)) {
        Some(config.getConfig(path))
      } else {
        None
      }
    }
  }

  implicit val file = new ValueReader[File] {
    override def read(
      config: Config,
      path: String
    ): File = {
      new File(config.getString(path))
    }
  }

  implicit val duration = new ValueReader[Duration] {
    override def read(
      config: Config,
      path: String
    ): Duration = {
      val text = config.getString(path)

      Duration(text)
    }
  }

  implicit val regexReader = new ValueReader[Regex] {
    override def read(
      config: Config,
      path: String
    ): Regex = {
      config.getString(path).r
    }
  }
}
