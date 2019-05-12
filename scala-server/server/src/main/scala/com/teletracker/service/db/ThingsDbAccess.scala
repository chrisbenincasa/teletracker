package com.teletracker.service.db

import com.teletracker.service.db.model._
import com.teletracker.service.inject.{DbImplicits, DbProvider}
import com.teletracker.service.util.ObjectMetadataUtils
import javax.inject.Inject
import scala.annotation.tailrec
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
  val users: Users,
  val trackedListThings: TrackedListThings,
  val trackedLists: TrackedLists,
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

  def findShowByIdBasic(id: Int, withAvailability: Boolean = true): Future[Option[PartialThing]] = {
    val showQuery = things.query.filter(t => t.id === id && t.`type` === ThingType.Show)

    val availabilityQuery = availabilities.query.filter(a => a.thingId === id).flatMap(av => {
      av.networkId_fk.map(_ -> av)
    })

    val movieFut = run(showQuery.result.headOption)
    val avFut = run(availabilityQuery.result)

    for {
      movie <- movieFut
      avs <- avFut
    } yield {
      val avWithDetails = avs.map { case (network, av) => av.withNetwork(network) }
      movie.map(m => m.asPartial.withAvailability(avWithDetails.toList))
    }
  }

  def findShowById(id: Int, withAvailability: Boolean = false) = {
    val showQuery = things.query.filter(t => t.id === id && t.`type` === ThingType.Show)

    val baseEpisodesQuery = tvShowSeasons.query.filter(_.showId === id) joinLeft
      tvShowEpisodes.query on (_.id === _.seasonId)

    val seasonsEpisodesFut = if (withAvailability) {
      run {
        baseEpisodesQuery.
          joinLeft(availability.query).
          on(_._2.map(_.id) === _.tvShowEpisodeId).
          result
      }
    } else {
      run(baseEpisodesQuery.result).map(seasonsAndEpisodes => {
        seasonsAndEpisodes.map { case (season, episode) => ((season, episode), Option.empty[Availability]) }
      })
    }

    val showAndNetworksFut = run {
      showQuery.
        joinLeft(thingNetworks.query).on(_.id === _.thingId).
        joinLeft(networks.query).on(_._2.map(_.networkId) === _.id).
        result
    }

    (for {
      showAndNetworks <- showAndNetworksFut
      seasonsEpisodes <- seasonsEpisodesFut
    } yield {
      showAndNetworks.map { case ((foundShow, _), showNetworks) =>
        val (seasonsAndEpisodes, availabilities) = seasonsEpisodes.unzip
        val (seasons, episodes) = seasonsAndEpisodes.unzip
        (foundShow, showNetworks, seasons, episodes, availabilities)
      }
    }).map {
      case results if results.isEmpty => None
      case results =>
        val show = results.map(_._1).head
        val networks = results.flatMap(_._2).distinct
        val seasons = results.flatMap(_._3).distinct
        val episodesBySeason = results.flatMap(_._4).flatten.groupBy(_.seasonId)
        val availabilityByEpisode = results.flatMap(_._5).flatten.collect { case x if x.tvShowEpisodeId.isDefined => x.tvShowEpisodeId.get -> x }.toMap

        val twd = PartialThing(
          id = Some(show.id.get),
          name = Some(show.name),
          normalizedName = Some(show.normalizedName),
          `type` = Some(show.`type`),
          createdAt = Some(show.createdAt),
          lastUpdatedAt = Some(show.lastUpdatedAt),
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

    val availabilityQuery = availabilities.query.filter(a => a.thingId === id).flatMap(av => {
      av.networkId_fk.map(_ -> av)
    })

    val movieFut = run(showQuery.result.headOption)
    val avFut = run(availabilityQuery.result)

    for {
      movie <- movieFut
      avs <- avFut
    } yield {
      val avWithDetails = avs.map { case (network, av) => av.withNetwork(network) }
      movie.map(m => m.asPartial.withAvailability(avWithDetails.toList))
    }
  }

  def findPersonById(id: Int) = {
    val person = things.query.filter(p => p.id === id && p.`type` === ThingType.Person)

    run {
      person.result.headOption
    }
  }

  def findThingsByExternalIds(source: ExternalSource, ids: Set[String], typ: Option[ThingType]): Future[Seq[(ExternalId, Thing)]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        val baseQuery = source match {
          case ExternalSource.TheMovieDb => externalIds.query.filter(_.tmdbId inSetBind ids)
          case _ => throw new IllegalArgumentException(s"Cannot get things by external source = $source")
        }

        baseQuery.flatMap(eid => {
          (typ match {
            case Some(t) => eid.thing.filter(_.`type` === t)
            case None => eid.thing
          }).map(eid -> _)
        }).result
      }
    }
  }

  def findRawThingsByExternalIds(source: ExternalSource, ids: Set[String], typ: Option[ThingType]): Future[Seq[(ExternalId, ThingRaw)]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        val baseQuery = source match {
          case ExternalSource.TheMovieDb => externalIds.query.filter(_.tmdbId inSetBind ids)
          case _ => throw new IllegalArgumentException(s"Cannot get things by external source = $source")
        }

        baseQuery.flatMap(eid => {
          (typ match {
            case Some(t) => eid.thingRaw.filter(_.`type` === t)
            case None => eid.thingRaw
          }).map(eid -> _)
        }).result
      }
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
        externalIds.query.filter(_.tmdbId === id).flatMap(_.thing).result.flatMap {
          case foundThings if foundThings.isEmpty =>
            things.query.
              filter(_.normalizedName === thing.normalizedName).
              filter(_.`type` === thing.`type`).
              result

          case foundThings => DBIO.successful(foundThings)

        }.flatMap(foundThings => {
          DBIO.successful {
            foundThings.find(thing => {
              thing.metadata.exists(ObjectMetadataUtils.metadataMatchesId(_, source, thing.`type`, id))
            })
          }
        })

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
      val thingAvailabilities = avs.filter(_.thingId.isDefined).groupBy(_.thingId.get)
      val tvEpisodeAvailabilities = avs.filter(_.tvShowEpisodeId.isDefined).groupBy(_.tvShowEpisodeId.get)

      def findInsertsAndUpdates(incoming: List[Availability], existing: Set[Availability]) = {
        @tailrec def findInsertsAndUpdates0(
          in: List[Availability],
          remaining: Set[Availability] = existing,
          updates: Set[Availability] = Set.empty,
          inserts: Set[Availability] = Set.empty
        ): (Set[Availability], Set[Availability]) = {
          in match {
            case Nil => inserts -> updates
            case x :: xs =>
              remaining.find(_.matches(x)) match {
                case Some(y) => findInsertsAndUpdates0(xs, remaining - y, updates + x.copy(id = y.id), inserts)
                case None => findInsertsAndUpdates0(xs, remaining, updates, inserts + x)
              }
          }
        }

        findInsertsAndUpdates0(incoming)
      }

      val thingAvailabilitySave = if (thingAvailabilities.nonEmpty) {
        run {
          availabilities.query.filter(_.thingId inSetBind thingAvailabilities.keySet).result
        }.flatMap(existing => {
          val existingByThingId = existing.filter(_.thingId.isDefined).groupBy(_.thingId.get)
          val (updates, inserts) = thingAvailabilities.foldLeft((Set.empty[Availability], Set.empty[Availability])) {
            case ((u, i), (thingId, toSave)) =>
              val existingForId = existingByThingId.getOrElse(thingId, Seq.empty)
              val (i2, u2) = findInsertsAndUpdates(toSave.toList, existingForId.toSet)
              (u ++ u2, i ++ i2)
          }

          val updateQueries = updates.toSeq.map(up => {
            availabilities.query.
              filter(a => a.id === up.id && a.thingId === up.thingId && a.offerType === up.offerType && a.networkId === up.networkId).
              update(up)
          })

          val insertQueries = availabilities.query ++= inserts

          run {
            DBIO.sequence(updateQueries).andThen(DBIO.seq(insertQueries))
          }
        })
      } else {
        Future.unit
      }

      val tvEpisodeAvailabilitySave = if (tvEpisodeAvailabilities.nonEmpty) {
        run {
          availabilities.query.filter(_.thingId inSetBind tvEpisodeAvailabilities.keySet).result
        }.flatMap(existing => {
          val existingByThingId = existing.filter(_.thingId.isDefined).groupBy(_.thingId.get)
          val (updates, inserts) = tvEpisodeAvailabilities.foldLeft((Set.empty[Availability], Set.empty[Availability])) {
            case ((u, i), (thingId, toSave)) =>
              val existingForId = existingByThingId.getOrElse(thingId, Seq.empty)
              val (i2, u2) = findInsertsAndUpdates(toSave.toList, existingForId.toSet)
              (u ++ u2, i ++ i2)
          }

          val updateQueries = updates.toSeq.map(up => {
            availabilities.query.
              filter(a => a.tvShowEpisodeId === up.tvShowEpisodeId && a.offerType === up.offerType && a.networkId === up.networkId).
              update(up)
          })

          val insertQueries = availabilities.query ++= inserts

          run {
            DBIO.sequence(updateQueries).andThen(DBIO.seq(insertQueries))
          }
        })
      } else {
        Future.unit
      }

      for {
        _ <- thingAvailabilitySave
        _ <- tvEpisodeAvailabilitySave
      } yield {}
    }
  }

  def getThingUserDetails(userId: Int, thingId: Int) = {
    // Do they track it?
    val lists = trackedListThings.query.filter(_.thingId === thingId).flatMap(l => {
      l.list_fk.filter(_.userId === userId)
    })

    run {
      lists.result
    }.map(lists => {
      UserThingDetails(lists.map(_.toFull))
    })
  }

  def getThingsUserDetails(userId: Int, thingIds: Set[Int]): Future[Map[Int, UserThingDetails]] = {
    val lists = trackedListThings.query.filter(_.thingId inSetBind thingIds).flatMap(l => {
      l.list_fk.filter(_.userId === userId).map(l.thingId -> _)
    })

    run {
      lists.result
    }.map(results => {
      results.groupBy(_._1).map {
        case (thingId, seq) => thingId -> UserThingDetails(seq.map(_._2.toFull))
      }
    })
  }
}

object UserThingDetails {
  def empty: UserThingDetails = UserThingDetails(Seq())
}

case class UserThingDetails(
  belongsToLists: Seq[TrackedList]
)
