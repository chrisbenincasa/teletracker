package com.teletracker.tasks.scraper

import com.teletracker.common.db.model.ThingRaw
import com.teletracker.common.model.tmdb.TmdbWatchable
import org.apache.commons.text.similarity.LevenshteinDistance

class ScrapedItemMatcher {
  def findMatch[W, T <: ScrapedItem](
    tmdbItem: W,
    item: T,
    titleMatchThreshold: Int
  )(implicit watchable: TmdbWatchable[W]
  ): Boolean = {
    val titlesEqual = watchable
      .title(tmdbItem)
      .exists(foundTitle => {
        val dist =
          LevenshteinDistance.getDefaultInstance
            .apply(foundTitle.toLowerCase(), item.title.toLowerCase())

        dist <= titleMatchThreshold
      })

    val releaseYearEqual = watchable
      .releaseYear(tmdbItem)
      .exists(tmdbReleaseYear => {
        item.releaseYear
          .map(_.trim.toInt)
          .exists(
            ry => (tmdbReleaseYear - 1 to tmdbReleaseYear + 1).contains(ry)
          )
      })

    titlesEqual && releaseYearEqual
  }

  def findMatch[T <: ScrapedItem](
    thingRaw: ThingRaw,
    item: T,
    titleMatchThreshold: Int
  ) = {
    val dist =
      LevenshteinDistance.getDefaultInstance
        .apply(thingRaw.name.toLowerCase(), item.title.toLowerCase())

    dist <= titleMatchThreshold
  }
}
