package com.teletracker.common.db.access

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.db.{BaseDbProvider, DbMonitoring, SyncDbProvider}
import com.teletracker.common.db.model._
import com.teletracker.common.util.GeneralizedDbFactory
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TvShowDbAccess @Inject()(
  val provider: BaseDbProvider,
  val things: Things,
  val tvShowSeasons: TvShowSeasons,
  val tvShowEpisodes: TvShowEpisodes,
  val externalIds: ExternalIds,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {
  import provider.driver.api._

  def findAllSeasonsForShow(showId: UUID) = {
    run {
      tvShowSeasons.query.filter(_.showId === showId).result
    }
  }

  def saveSeason(season: TvShowSeason) = {
    run {
      (tvShowSeasons.query returning
        tvShowSeasons.query.map(_.id) into
        ((season, id) => season.copy(id = Some(id)))) += season
    }
  }

  def findEpisodeByExternalId(
    source: ExternalSource,
    id: String
  ) = {
    val query = source match {
      case ExternalSource.TheMovieDb =>
        externalIds.query
          .filter(_.tmdbId === id)
          .flatMap(_.episode)
          .result
          .headOption
      case _ => DBIO.successful(None)
    }

    run(query)
  }

  def insertSeason(season: TvShowSeason) = {
    run {
      (tvShowSeasons.query returning
        tvShowSeasons.query.map(_.id) into
        ((s, id) => s.copy(id = Some(id)))) += season
    }
  }

  def insertEpisode(episode: TvShowEpisode) = {
    run {
      (tvShowEpisodes.query returning
        tvShowEpisodes.query.map(_.id) into
        ((ep, id) => ep.copy(id = Some(id)))) += episode
    }
  }

  def upsertEpisode(episode: TvShowEpisode) = {
    val c = episode.id
      .map(id => {
        run {
          tvShowEpisodes.query.filter(_.id === id).result.headOption
        }.map {
          case Some(found) => found
          case None        => episode.copy(id = None)
        }
      })
      .getOrElse {
        Future.successful(episode)
      }

    c.map {
        case model if model.id.isDefined =>
          tvShowEpisodes.query
            .filter(_.id === model.id)
            .update(model)
            .map(_ => model)
        case model =>
          (tvShowEpisodes.query returning
            tvShowEpisodes.query.map(_.id) into
            ((ep, id) => ep.copy(id = Some(id)))) += model
      }
      .flatMap(run(_))
  }
}