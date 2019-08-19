package com.teletracker.service.testing.integration

import com.teletracker.common.db.access.{
  NetworksDbAccess,
  ThingsDbAccess,
  TvShowDbAccess
}
import com.teletracker.common.db.model._
import com.teletracker.common.util.Slug
import com.teletracker.service.testing.framework.BaseSpecWithServer
import java.time.OffsetDateTime
import java.util.UUID

class TvShowSpec extends BaseSpecWithServer {
  "TV Shows" should "contain availability information" in {
    val thingDbAccess = injector.getInstance(classOf[ThingsDbAccess])
    val tvShowDbAccess = injector.getInstance(classOf[TvShowDbAccess])
    val networkDbAccess = injector.getInstance(classOf[NetworksDbAccess])

    val show =
      thingDbAccess
        .saveThing(
          Thing(
            UUID.randomUUID(),
            "Not Friends",
            Slug("Not Friends", 1999),
            ThingType.Show,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            None
          )
        )
        .await()

    val season =
      tvShowDbAccess
        .insertSeason(
          TvShowSeason(None, 1, show.id, None, None)
        )
        .await()

    val episode =
      tvShowDbAccess
        .insertEpisode(
          TvShowEpisode(
            None,
            1,
            show.id,
            season.id.get,
            "The One Where They Explode",
            None
          )
        )
        .await()

    val network =
      networkDbAccess
        .saveNetwork(
          Network(
            None,
            "Netflix",
            Slug.forString("Netflix"),
            "nflx",
            None,
            None
          )
        )
        .await()

    val availability =
      thingDbAccess
        .insertAvailability(
          Availability(
            None,
            true,
            Some("US"),
            None,
            None,
            None,
            Some(OfferType.Subscription),
            None,
            None,
            None,
            Some(episode.id.get),
            Some(network.id.get),
            None
          )
        )
        .await()

    val foundShow =
      thingDbAccess.findShowById(show.id, withAvailability = true).await()
    assert(foundShow.isDefined)
    assert(foundShow.get.id === show.id)
    assert(foundShow.get.seasons.getOrElse(Nil).length === 1)

    inside(foundShow.get.seasons) {
      case Some(foundSeason :: Nil) =>
        assert(foundSeason.id === season.id)

        inside(foundSeason.episodes) {
          case Some(foundEpisode :: Nil) =>
            assert(foundEpisode.id === episode.id)

            inside(foundEpisode.availability) {
              case Some(foundAvailability) =>
                assert(foundAvailability.id.isDefined)
                assert(
                  foundAvailability.tvShowEpisodeId === availability.flatMap(
                    _.tvShowEpisodeId
                  )
                )
            }
        }
    }
  }
}
