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
  val personThings: PersonThings,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext) extends DbAccess {
  import provider.driver.api._
  import dbImplicits._

  def findThingById(id: Int) = {
    run {
      things.query.filter(_.id === id).take(1).result.headOption
    }
  }

  def findThingByIds(ids: Set[Int]) = {
    things.query.filter(_.id inSetBind ids)
  }

  def findShowById(id: Int) = {
    val showQuery = things.query.filter(t => t.id === id && t.`type` === ThingType.Show)

    val seasonsEpisodes = tvShowSeasons.query.filter(_.showId === id) joinLeft
      tvShowEpisodes.query on (_.id === _.seasonId) joinLeft
      availability.query on (_._2.map(_.id) === _.tvShowEpisodeId)

    val showAndNetworks = showQuery joinLeft
      thingNetworks.query on (_.id === _.thingId) joinLeft
      networks.query on (_._2.map(_.networkId) === _.id)

    run {
      for {
        x <- showAndNetworks.result
        y <- seasonsEpisodes.result
      } yield {
        x.map { case ((foundShow, _), showNetworks) =>
          val (seasonsAndEpisodes, availabilities) = y.unzip
          val (seasons, episodes) = seasonsAndEpisodes.unzip
          (foundShow, showNetworks, seasons, episodes, availabilities)
        }
      }
    }.map {
      case results if results.isEmpty => None
      case results =>
        val show = results.map(_._1).head
        val networks = results.flatMap(_._2).distinct
        val seasons = results.flatMap(_._3).distinct
        val episodesBySeason = results.flatMap(_._4).flatten.groupBy(_.seasonId)
        val availabilityByEpisode = results.flatMap(_._5).flatten.collect { case x if x.tvShowEpisodeId.isDefined => x.tvShowEpisodeId.get -> x }.toMap

        val twd = ThingWithDetails(
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
                season.overview,
                season.airDate,
                episodes
              )
            }).toList
          ),
          metadata = show.metadata
        )

        Some(twd)
    }
  }

  def findMovieById(id: Int) = {
    val showQuery = things.query.filter(t => t.id === id && t.`type` === ThingType.Movie).take(1)

    run {
      showQuery.result.headOption
    }
  }

  def findPersonById(id: Int) = {
    val person = things.query.filter(p => p.id === id && p.`type` === ThingType.Person)

    run {
      person.result.headOption
    }
  }

  def findThingsByExternalIds(source: ExternalSource, ids: Set[String], typ: ThingType) = {
    run {
      val baseQuery = source match {
        case ExternalSource.TheMovieDb => externalIds.query.filter(_.tmdbId inSetBind ids)
        case _ => throw new IllegalArgumentException(s"Cannot get things by external source = $source")
      }

      baseQuery.flatMap(eid => {
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

  def findExternalIdsByTmdbId(tmdbId: String) = {
    run {
      externalIds.query.filter(_.tmdbId === tmdbId).result.headOption
    }
  }

  def upsertPersonThing(personThing: PersonThing) = {
    run {
      personThings.query.insertOrUpdate(personThing).map(_ => personThing)
    }
  }

  def upsertExternalIds(externalId: ExternalId) = {
    val q = if (externalId.thingId.isDefined) {
      Some(externalIds.query.filter(_.thingId === externalId.thingId))
    } else if (externalId.tvEpisodeId.isDefined) {
      Some(externalIds.query.filter(_.thingId === externalId.tvEpisodeId))
    } else {
      None
    }

    q.map(_.result.headOption).map(_.flatMap {
      case None =>
        (externalIds.query returning
          externalIds.query.map(_.id) into
          ((eid, id) => eid.copy(id = Some(id)))) += externalId

      case Some(e) =>
        val updated = externalId.copy(id = e.id)
        externalIds.query.filter(_.id === e.id).update(updated).map(_ => updated)
    }).map(run).getOrElse(Future.failed(new IllegalArgumentException("externalId had neither thingId nor tvEpisodeId defined")))
  }

  def getAllGenres(typ: Option[GenreType]) = {
    run {
      val q = genres.query
      val filtered = if (typ.isDefined) q.filter(_.`type` === typ.get) else q
      filtered.result
    }
  }

  def saveGenreAssociation(thingGenre: ThingGenre) = {
    run {
      thingGenres.query.insertOrUpdate(thingGenre)
    }
  }

  def saveAvailability(av: Availability) = {
    run {
      (availabilities.query returning
        availabilities.query.map(_.id) into
        ((av, id) => av.copy(id = Some(id)))) += av
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
