package com.teletracker.common.util

object Sanitizers {
  def normalizeQuotes(str: String): String = {
    str.replaceAll("[\u0091\u0092\u2018\u2019\u201B]", "'")
  }
}
