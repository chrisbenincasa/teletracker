package com.teletracker.common.db

object Bookmark {
  def parse(bookmark: String): Bookmark = {
    val Array(sort, value) = bookmark.split("\\|", 2)
    val Array(sortType, isDesc) = sort.split("_", 2)
    Bookmark(sortType, isDesc.toBoolean, value)
  }
}

case class Bookmark(
  sortType: String,
  desc: Boolean,
  value: String) {

  def asString: String = {
    s"${sortType}_$desc|$value"
  }
}
