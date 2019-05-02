package com.teletracker.service.db

object H2Helpers {
  val KEEP_ALIVE_SETTING = (delay: Int) => "DB_CLOSE_DELAY" -> s"$delay"
  val KEEP_ALIVE_SETTING_DEFAULT = KEEP_ALIVE_SETTING(-1)
  val DATABASE_TO_UPPER = (on: Boolean) => "DATABASE_TO_UPPER" -> s"$on"
  val DATABASE_TO_UPPER_DEFAULT = DATABASE_TO_UPPER(false)

  val DEFAULT_SETTINGS = Map(KEEP_ALIVE_SETTING_DEFAULT, DATABASE_TO_UPPER_DEFAULT)

  def url(path: String, settings: Map[String, String] = DEFAULT_SETTINGS): String = {
    val urlOptions = settings.toList.map(tup => s"${tup._1}=${tup._2}").mkString(";", ";", "")
    s"jdbc:h2:$path;$urlOptions"
  }
}