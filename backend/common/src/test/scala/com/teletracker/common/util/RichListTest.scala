package com.teletracker.common.util

import Lists._
import org.scalatest.flatspec.AnyFlatSpec

class RichListTest extends AnyFlatSpec {
  "RichList" should "removeDupes with condition" in {
    val l: List[Option[Int]] =
      List(Some(1), None, Some(1), Some(2), Some(3), Some(20), None)

    val deduped = l.removeDupesWhere { case Some(t) => t }

    println(deduped)
  }
}
