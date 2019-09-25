package com.teletracker.common.db

object Bookmark {
  def parse(bookmark: String): Bookmark = {
    val Array(sort, value) = bookmark.split("\\|", 2)

    val (finalValue, refinementValue) = value.split("\\|", 2) match {
      case Array(oneValue)             => oneValue -> None
      case Array(oneValue, refinement) => oneValue -> Some(refinement)
      case _                           => throw new IllegalArgumentException
    }

    val Array(sortType, isDesc) = sort.split("_", 2)
    Bookmark(sortType, isDesc.toBoolean, finalValue, refinementValue)
  }

  def apply(
    sortMode: SortMode,
    value: String,
    refinement: Option[String]
  ): Bookmark = {
    Bookmark(sortMode.`type`, sortMode.isDesc, value, refinement)
  }

  def unapply(arg: Bookmark): Option[(String, Boolean, String)] = {
    Some(arg.sortType, arg.desc, arg.value)
  }
}

case class Bookmark(
  sortType: String,
  desc: Boolean,
  value: String,
  valueRefinement: Option[String]) {

  def asString: String = {
    s"${sortType}_$desc|$value"
  }

  def sortMode: SortMode = {
    SortMode.fromString(sortType).direction(desc)
  }
}
