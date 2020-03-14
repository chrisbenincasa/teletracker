package com.teletracker.common.util

import org.scalatest.FlatSpec

class SlugTest extends FlatSpec {
  it should "find the next slug" in {
    val newSlug = Slug.raw("abc-xyz")
    val existingSlugs = List(newSlug, newSlug.addSuffix("2"))

    val nextSlug = Slug.findNext(newSlug, existingSlugs)

    assert(nextSlug.get == Slug.raw("abc-xyz-3"))
  }
}
