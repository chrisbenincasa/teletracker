package com.chrisbenincasa.services.teletracker.db

import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.{DbImplicits, DbProvider}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ThingsDbAccess @Inject()(
  val provider: DbProvider,
  val things: Things,
  val tvShowSeasons: TvShowSeasons,
  val thingNetworks: ThingNetworks,
  val networks: Networks,
  val networkReferences: NetworkReferences,
  val tvShowEpisodes: TvShowEpisodes,
  val availability: Availabilities,
  val externalIds: ExternalIds,
  val genres: Genres,
  val genreReferences: GenreReferences,
  val thingGenres: ThingGenres,
  val availabilities: Availabilities,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext) extends DbAccess {
  import provider.driver.api._
  import dbImplicits._

  def findThingById(id: Int) = {
    things.query.filter(_.id === id).take(1)
  }

  def findThingByIds(ids: Set[Int]) = {
    things.query.filter(_.id inSetBind ids)
  }

  def findShowById(id: Int) = {
    val show = things.query.filter(_.id === id)

    val seasonsEpisodes = tvShowSeasons.query.filter(_.showId === id) joinLeft
      tvShowEpisodes.query on (_.id === _.seasonId) joinLeft
      availability.query on (_._2.map(_.id) === _.tvShowEpisodeId)

    val showAndNetworks = show joinLeft
      thingNetworks.query on (_.id === _.thingId) joinLeft
      networks.query on (_._2.map(_.networkId) === _.id)

    run {
      for {
        x <- showAndNetworks.result
        y <- seasonsEpisodes.result
      } yield {
        x.map { case ((show, _), networks) =>
          val (seasonsAndEpisodes, availabilities) = y.unzip
          val (seasons, episodes) = seasonsAndEpisodes.unzip
          (show, networks, seasons, episodes, availabilities)
        }
      }
    }
  }

  def findThingsByExternalIds(source: String, ids: Set[String], typ: String) = {
    run {
      externalIds.query.filter(_.tmdbId inSetBind ids).flatMap(eid => {
        eid.thing.filter(_.`type` === typ).map(eid -> _)
      }).result
    }
  }

  def findTmdbGenres(ids: Set[Int]) = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        genreReferences.query.
          filter(_.externalSource === ExternalSource.TheMovieDb).
          filter(_.externalId inSetBind ids.map(_.toString)).
          flatMap(_.genre).
          result
      }
    }
  }

  def saveThing(thing: Thing, externalPair: Option[(ExternalSource, String)] = None) = {
    val existing = externalPair match {
      case Some((source, id)) if source == ExternalSource.TheMovieDb =>
        externalIds.query.filter(_.tmdbId === id).flatMap(_.thing).result.headOption

      case _ =>
        things.query.
          filter(_.normalizedName === thing.normalizedName).
          filter(_.`type` === thing.`type`).
          result.
          headOption
    }

    val insertOrUpdate = existing.flatMap {
      case None =>
        (things.query returning
          things.query.map(_.id) into
          ((t, id) => t.copy(id = Some(id)))) += thing

      case Some(e) =>
        val updated = thing.copy(id = e.id)
        things.query.filter(t => {
          t.id === e.id &&
            t.normalizedName === e.normalizedName &&
            t.`type` === e.`type`
        }).update(updated).map(_ => updated)
    }

    run {
      insertOrUpdate
    }
  }

  def upsertExternalIds(externalId: ExternalId) = {
    val action = if (externalId.thingId.isDefined) {
      externalIds.query.filter(_.thingId === externalId.thingId).result.headOption.flatMap {
        case None =>
          externalIds.query += externalId

        case Some(e) =>
          externalIds.query.filter(_.id === e.id).update(externalId.copy(id = e.id))
      }
    } else if (externalId.tvEpisodeId.isDefined) {
      externalIds.query.filter(_.thingId === externalId.tvEpisodeId).result.headOption.flatMap {
        case None =>
          externalIds.query += externalId

        case Some(e) =>
          externalIds.query.filter(_.id === e.id).update(externalId.copy(id = e.id))
      }
    } else {
      DBIO.successful(0)
    }

    run(action)
  }

  def saveGenreAssociation(thingGenre: ThingGenre) = {
    run {
      thingGenres.query.insertOrUpdate(thingGenre)
    }
  }

  def saveNetworkAssociation(thingNetwork: ThingNetwork) = {
    run {
      thingNetworks.query.insertOrUpdate(thingNetwork)
    }
  }

  def findNetworksBySlugs(slugs: Set[String]) = {
    if (slugs.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        networks.query.filter(_.slug inSetBind slugs).result
      }
    }
  }

  def findAllNetworks() = {
    run {
      networkReferences.query.flatMap(ref => ref.networkId_fk.map(ref -> _)).result
    }
  }

  def saveAvailabilities(avs: Seq[Availability]) = {
    if (avs.isEmpty) {
      Future.successful(None)
    } else {
      run {
        availabilities.query ++= avs
      }
    }
  }
}
