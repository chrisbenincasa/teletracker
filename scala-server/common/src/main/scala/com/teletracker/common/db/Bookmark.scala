package com.teletracker.common.db

import java.util.Base64

object Bookmark {
  def parse(bookmark: String): Bookmark = {
    val decoded = new String(Base64.getDecoder.decode(bookmark))

    val parts = decoded.split("\\|")

    if (parts.length < 3 || parts.length > 4) {
      throw new IllegalArgumentException(s"Invalid bookmark format: $decoded")
    } else if (parts.length == 3) {
      val Array(sortType, isDesc, value) = parts
      Bookmark(sortType, isDesc.toBoolean, value, None)
    } else {
      val Array(sortType, isDesc, value, refinement) = parts
      Bookmark(sortType, isDesc.toBoolean, value, Some(refinement))
    }
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

  final private val base64Encoder = Base64.getEncoder
}

case class Bookmark(
  sortType: String,
  desc: Boolean,
  value: String,
  valueRefinement: Option[String]) {

  def asString: String = {
    Bookmark.base64Encoder.encodeToString(
      s"${sortType}|$desc|$value${valueRefinement.map("|" + _).getOrElse("")}".getBytes
    )
  }

  def sortMode: SortMode = {
    SortMode.fromString(sortType).direction(desc)
  }
}
