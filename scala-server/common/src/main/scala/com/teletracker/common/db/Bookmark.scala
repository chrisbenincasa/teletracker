package com.teletracker.common.db

import scala.util.Try

object Bookmark {
  def parse(bookmark: String): Bookmark = {
    val Array(sort, value) = bookmark.split("\\|", 2)
    val Array(sortType, isDesc) = sort.split("_", 2)
    Bookmark(sortType, isDesc.toBoolean, value)
  }

  def apply(
    sortMode: SortMode,
    value: String
  ): Bookmark = {
    Bookmark(sortMode.`type`, sortMode.isDesc, value)
  }

  def unapply(arg: Bookmark): Option[(String, Boolean, String)] = {
    Some(arg.sortType, arg.desc, arg.value)
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
