package com.teletracker.service.testing

import com.teletracker.service.db._
import com.teletracker.service.db.model._
import com.teletracker.service.testing.framework.BaseSpecWithServer
import com.teletracker.service.util.Slug
import org.joda.time.DateTime

class TvShowSpec extends BaseSpecWithServer {
  "TV Shows" should "contain availability information" in {
    val thingDbAccess = injector.getInstance(classOf[ThingsDbAccess])
    val tvShowDbAccess = injector.getInstance(classOf[TvShowDbAccess])
    val networkDbAccess = injector.getInstance(classOf[NetworksDbAccess])

    val show =
      thingDbAccess
        .saveThing(
          Thing(
            None,
            "Not Friends",
            Slug("Not Friends"),
            ThingType.Show,
            DateTime.now(),
            DateTime.now(),
            None
          )
        )
        .await()

    val season =
      tvShowDbAccess
        .insertSeason(
          TvShowSeason(None, 1, show.id.get, None, None)
        )
        .await()

    val episode =
      tvShowDbAccess
        .insertEpisode(
          TvShowEpisode(
            None,
            1,
            show.id.get,
            season.id.get,
            "The One Where They Explode",
            None
          )
        )
        .await()

    val network =
      networkDbAccess
        .saveNetwork(
          Network(None, "Netflix", Slug("Netflix"), "nflx", None, None)
        )
        .await()

    val availability =
      thingDbAccess
        .saveAvailability(
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
      thingDbAccess.findShowById(show.id.get, withAvailability = true).await()
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
                  foundAvailability.tvShowEpisodeId === availability.tvShowEpisodeId
                )
            }
        }
    }
  }
}
