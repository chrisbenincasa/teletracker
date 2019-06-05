package com.teletracker.service.testing

import com.teletracker.service.util.Slug
import org.scalatest.{FlatSpec, Matchers}

class SlugSpec extends FlatSpec with Matchers {
  "Slugs" should "remove dashes, colons, and extra whitespace" in {
    val slug = Slug("Star Wars: Episode III - Revenge of the Sith", 2005)
    slug.toString shouldEqual "star-wars-episode-iii-revenge-of-the-sith-2005"
  }
}
