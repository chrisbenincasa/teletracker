package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.db.model.{ThingWithDetails, TvShowSeasonWithEpisodes}
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TvShowController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1") {
    get("/show/:id") { req: GetShowRequest =>
      thingsDbAccess.findShowById(req.id).map(results => {
        if (results.isEmpty) {
          response.status(404)
        } else {
          val show = results.map(_._1).head
          val networks = results.flatMap(_._2).distinct
          val seasons = results.flatMap(_._3).distinct
          val episodesBySeason = results.flatMap(_._4).flatten.groupBy(_.seasonId)
          val availabilityByEpisode = results.flatMap(_._5).flatten.collect { case x if x.tvShowEpisodeId.isDefined => x.tvShowEpisodeId.get -> x }.toMap

          val result = ThingWithDetails(
            id = show.id.get,
            name = show.name,
            normalizedName = show.normalizedName,
            `type` = show.`type`,
            createdAt = show.createdAt,
            lastUpdatedAt = show.lastUpdatedAt,
            networks = Option(networks.toList),
            seasons = Some(
              seasons.map(season => {
                val episodes = season.id.flatMap(episodesBySeason.get).map(_.toList).
                  map(_.map(ep => ep.withAvailability(availabilityByEpisode.get(ep.id.get))))

                TvShowSeasonWithEpisodes(
                  season.id,
                  season.number,
                  episodes
                )
              }).toList
            ),
            metadata = show.metadata
          )

          response.ok.contentTypeJson().body(DataResponse.complex(result))
        }
      })
    }
  }
}

case class GetShowRequest(@RouteParam id: Int)
