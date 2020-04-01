package com.teletracker.common.util

import org.scalatest.FlatSpec
import scala.util.{Failure, Success}

class SlugTest extends FlatSpec {
  "Slug.findNext" should "find the next slug" in {
    val newSlug = Slug.raw("abc-xyz")
    val existingSlugs = List(newSlug, newSlug.addSuffix("2"))

    val nextSlug = Slug.findNext(newSlug, existingSlugs)

    assert(nextSlug.get == Slug.raw("abc-xyz-3"))
  }

  it should "not fail on prefixed non-dupes" in {
    val newSlug = Slug.raw("yuan-yuan")
    val existingSlugs = List(newSlug, newSlug, newSlug.addSuffix("tan"))

    Slug.findNext(newSlug, existingSlugs) match {
      case Failure(exception) => fail(exception)
      case Success(value) =>
        println(value)
        succeed
    }
  }
}
