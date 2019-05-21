package com.teletracker.service.config

import com.typesafe.config.Config
import java.sql.Driver
import net.ceedubs.ficus.readers.ValueReader

object CustomReaders {
  implicit val classReader = new ValueReader[Driver] {
    override def read(
      config: Config,
      path: String
    ): Driver = {
      Class.forName(config.getString(path)).newInstance().asInstanceOf[Driver]
    }
  }
}
