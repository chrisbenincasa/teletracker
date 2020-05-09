package com.teletracker.service.testing

import com.teletracker.common.util.Slug
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SlugSpec extends AnyFlatSpec with Matchers {
  "Slugs" should "remove dashes, colons, and extra whitespace" in {
    val slug = Slug("Star Wars: Episode III - Revenge of the Sith", 2005)

    val expected = "star-wars-episode-iii-revenge-of-the-sith-2005"

    slug.toString shouldEqual expected

    Slug.raw(expected).toString shouldEqual expected
  }
}
