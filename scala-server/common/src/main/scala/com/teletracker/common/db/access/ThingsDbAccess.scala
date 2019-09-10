package com.teletracker.common.db.access

import com.teletracker.common.db.DbMonitoring
import com.teletracker.common.db.model._
import com.teletracker.common.db.util.InhibitFilter
import com.teletracker.common.inject.{BaseDbProvider, DbImplicits}
import com.teletracker.common.util.{Field, Slug}
import org.postgresql.util.PSQLException
import slick.jdbc.{PositionedParameters, SetParameter}
import java.sql.JDBCType
import java.time._
import java.util.UUID
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

abstract class ThingsDbAccess(
  override val provider: BaseDbProvider,
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
  val trackedListThings: TrackedListThings,
  val trackedLists: TrackedLists,
  val userThingTags: UserThingTags,
  val collections: Collections,
  val people: People,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends DbAccess(dbMonitoring) {
  import dbImplicits._
  import provider.driver.api._

  def findThingById(id: UUID): Future[Option[Thing]] = {
    run {
      things.query.filter(_.id === id).take(1).result.headOption
    }
  }

  def findThingByIdRaw(id: UUID): Future[Option[ThingRaw]] = {
    run {
      things.rawQuery.filter(_.id === id).take(1).result.headOption
    }
  }

  def findThingBySlug(slug: Slug) = {
    run {
      things.query.filter(_.normalizedName === slug).take(1).result.headOption
    }
  }

  def findThingBySlugRaw(slug: Slug) = {
    run {
      things.rawQuery
        .filter(_.normalizedName === slug)
        .take(1)
        .result
        .headOption
    }
  }

  def findThingsBySlugsRaw(slugs: Set[Slug]) = {
    run {
      things.rawQuery
        .filter(_.normalizedName inSetBind slugs)
        .result
    }.map(_.map(t => t.normalizedName -> t).toMap)
  }

  def findThingByIdOrSlug(idOrSlug: Either[UUID, Slug]) = {
    idOrSlug.fold(
      findThingById,
      findThingBySlug
    )
  }

  def findThingsByNormalizedName(name: String): Future[Seq[ThingRaw]] = {
    run {
      things.rawQuery.filter(_.normalizedName === Slug.forString(name)).result
    }
  }

  def loopThroughAllThings(
    offset: Int = 0,
    perPage: Int = 50,
    limit: Int = -1,
    startingId: Option[UUID] = None,
    thingType: Option[ThingType] = None
  )(
    process: Seq[ThingRaw] => Future[Unit]
  ): Future[Unit] = {
    if (limit > 0 && offset >= limit) {
      Future.unit
    } else {
      run {
        val res = InhibitFilter(things.rawQuery)
          .filter(startingId)(id => _.id >= id)
          .filter(thingType)(t => _.`type` === t)
          .query
          .sortBy(_.id.asc)
          .drop(offset)
          .take(perPage)
          .result

        println(res.statements)

        res
      }.flatMap {
        case x if x.isEmpty => Future.unit
        case x =>
          process(x).flatMap(
            _ => loopThroughAllThings(offset + perPage, perPage, limit)(process)
          )
      }
    }
  }

  def loopThroughAllPeople(
    offset: Int = 0,
    perPage: Int = 50,
    limit: Int = -1
  )(
    process: Seq[Person] => Future[Unit]
  ): Future[Unit] = {
    if (limit > 0 && offset >= limit) {
      Future.unit
    } else {
      run {
        people.query
          .drop(offset)
          .take(perPage)
          .sortBy(t => (t.tmdbId.asc.nullsLast, t.id))
          .result
      }.flatMap {
        case x if x.isEmpty => Future.unit
        case x =>
          process(x).flatMap(
            _ => loopThroughAllPeople(offset + perPage, perPage, limit)(process)
          )
      }
    }
  }

  private val logarithm = SimpleFunction.unary[Double, Double]("log")
  private val NonLatin = Pattern.compile("[^\\w-]")

  def searchForThings(
    query: String,
    options: SearchOptions
  ): Future[Seq[ThingRaw]] = {
    if (query.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      val terms =
        query
          .split(" ")
          .map(
            NonLatin
              .matcher(_)
              .replaceAll("")
              .replaceAllLiterally("'", "")
          )
          .filter(_.nonEmpty)

      val first = terms.init
      val last = terms.last + ":*"
      val finalQuery = (first :+ last).mkString(" & ")

      def mkQuery(s: Rep[String]) = {
        toTsQuery(s, Some("simple"))
      }

      def mkVector(s: Rep[String]) = {
        toTsVector(
          s,
          Some("simple")
        )
      }

      def voteAverage(thing: this.things.ThingsTableRaw) = {
        thing.metadata
          .map(m => {
            (m +> "themoviedb" +> "movie" +>> "vote_average")
              .asColumnOf[Double]
              .ifNull(
                (m +> "themoviedb" +> "show" +>> "vote_average")
                  .asColumnOf[Double]
              )
              .ifNull(0.0)
          })
      }

      def voteCount(thing: this.things.ThingsTableRaw) = {
        thing.metadata
          .map(m => {
            (m +> "themoviedb" +> "movie" +>> "vote_count")
              .asColumnOf[Double]
              .ifNull(
                (m +> "themoviedb" +> "show" +>> "vote_count")
                  .asColumnOf[Double]
              )
              .ifNull(0.0)
          })
      }

      val baseSearch =
        things.rawQuery
          .filter(t => {
            mkQuery(finalQuery.bind) @@ mkVector(t.name)
          })

      val additionalFilters =
        InhibitFilter(baseSearch)
          .filter(options.thingTypeFilter) {
            case thingTypes if thingTypes.size == 1 =>
              t => t.`type` === thingTypes.head
            case thingTypes =>
              t => t.`type` inSetBind thingTypes
          }
          .query

      run("search") {
        val q = additionalFilters
          .sortBy {
            case thing if options.rankingMode == SearchRankingMode.Popularity =>
              thing.popularity.desc.nullsLast

            case thing
                if options.rankingMode == SearchRankingMode.PopularityAndVotes =>
              (logarithm(thing.popularity.ifNull(1.0)) + ((voteCount(thing) * voteAverage(
                thing
              )) / 150000.0)).desc.nullsLast
          }
          .take(20)
          .result

        q.statements.foreach()

      }.recover {
        case e: PSQLException =>
          println(s"Bad query: $finalQuery")
          throw e
      }
    }
  }

  private val defaultFields = List(Field("id"))

  def findThingsByIds(
    ids: Set[UUID],
    selectFields: Option[List[Field]]
  ): Future[Map[UUID, ThingRaw]] = {
    run {
      things.rawQuery.filter(_.id inSetBind ids).result
    }.map(
      _.map(_.selectFields(selectFields, defaultFields))
        .map(thing => thing.id -> thing)
        .toMap
    )
  }

  def findThingByIds(ids: Set[UUID]): Query[things.ThingsTable, Thing, Seq] = {
    things.query.filter(_.id inSetBind ids)
  }

  def findShowById(
    id: UUID,
    withAvailability: Boolean = false
  ): Future[Option[PartialThing]] = {
    val showQuery =
      things.rawQuery.filter(t => t.id === id && t.`type` === ThingType.Show)

    val baseEpisodesQuery = tvShowSeasons.query.filter(_.showId === id) joinLeft
      tvShowEpisodes.query on (_.id === _.seasonId)

    val seasonsEpisodesFut = if (withAvailability) {
      run {
        baseEpisodesQuery
          .joinLeft(availability.query)
          .on(_._2.map(_.id) === _.tvShowEpisodeId)
          .result
      }
    } else {
      run(baseEpisodesQuery.result).map(seasonsAndEpisodes => {
        seasonsAndEpisodes.map {
          case (season, episode) =>
            ((season, episode), Option.empty[Availability])
        }
      })
    }

    val showAndNetworksFut = run {
      showQuery
        .joinLeft(thingNetworks.query)
        .on(_.id === _.thingId)
        .joinLeft(networks.query)
        .on(_._2.map(_.networkId) === _.id)
        .result
    }

    (for {
      showAndNetworks <- showAndNetworksFut
      seasonsEpisodes <- seasonsEpisodesFut
    } yield {
      showAndNetworks.map {
        case ((foundShow, _), showNetworks) =>
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
        val availabilityByEpisode = results
          .flatMap(_._5)
          .flatten
          .collect {
            case x if x.tvShowEpisodeId.isDefined => x.tvShowEpisodeId.get -> x
          }
          .toMap

        val twd = PartialThing(
          id = show.id,
          name = Some(show.name),
          normalizedName = Some(show.normalizedName),
          `type` = Some(show.`type`),
          createdAt = Some(show.createdAt),
          lastUpdatedAt = Some(show.lastUpdatedAt),
          networks = Option(networks.toList),
          seasons = Some(
            seasons
              .map(season => {
                val episodes = season.id
                  .flatMap(episodesBySeason.get)
                  .map(_.toList)
                  .map(
                    _.map(
                      ep =>
                        ep.withAvailability(
                          availabilityByEpisode.get(ep.id.get)
                        )
                    )
                  )

                TvShowSeasonWithEpisodes(
                  season.id,
                  season.number,
                  season.overview,
                  season.airDate,
                  episodes
                )
              })
              .toList
          ),
          metadata = show.metadata
        )

        Some(twd)
    }
  }

  def findThingById(
    id: UUID,
    typ: ThingType
  ): Future[Option[PartialThing]] = {
    val thingQuery = things.query
      .filter(t => t.id === id && t.`type` === typ)
      .take(1)

    val availabilityQuery = availabilities.query
      .filter(a => a.thingId === id)
      .flatMap(av => {
        av.networkId_fk.map(_ -> av)
      })

    val thingFut = run(thingQuery.result.headOption)
    val avFut = run(availabilityQuery.result)

    for {
      thing <- thingFut
      avs <- avFut
    } yield {
      val avWithDetails = avs.map {
        case (network, av) => av.withNetwork(network)
      }

      thing.map(_.toPartial.withAvailability(avWithDetails.toList))
    }
  }

  def findThingBySlug(
    slug: Slug,
    thingType: Option[ThingType]
  ): Future[Option[PartialThing]] = {
    val thingQuery = InhibitFilter(
      things.query.filter(_.normalizedName === slug)
    ).filter(thingType)(typ => _.`type` === typ).query.take(1)

    val availabilityQuery = thingQuery.flatMap(q => {
      availabilities.query
        .filter(a => a.thingId === q.id)
        .flatMap(av => {
          av.networkId_fk.map(_ -> av)
        })
    })

    val thingFut = run(thingQuery.result.headOption)
    val avFut = run(availabilityQuery.result)

    for {
      thing <- thingFut
      avs <- avFut
    } yield {
      val avWithDetails = avs.map {
        case (network, av) => av.withNetwork(network)
      }
      thing.map(_.toPartial.withAvailability(avWithDetails.toList))
    }
  }

  def findThingBySlug(
    slug: Slug,
    typ: ThingType
  ): Future[Option[PartialThing]] = {
    findThingBySlug(slug, Some(typ))
  }

  def findPersonById(id: UUID): Future[Option[Person]] = {
    val person =
      people.query.filter(p => p.id === id)

    run {
      person.result.headOption
    }
  }

  def findPersonBySlug(slug: Slug): Future[Option[Person]] = {
    val person =
      people.query.filter(p => p.normalizedName === slug)

    run {
      person.result.headOption
    }
  }

  def findPeopleByTmdbIds(ids: Set[String]): Future[Map[String, Person]] = {
    if (ids.isEmpty) {
      Future.successful(Map.empty)
    } else {
      run {
        people.query
          .filter(_.tmdbId.isDefined)
          .filter(_.tmdbId inSetBind ids)
          .map(p => (p.tmdbId.get -> p))
          .result
      }.map(_.toMap)
    }
  }

  def findPeopleIdsByTmdbIds(ids: Set[String]): Future[Seq[(String, UUID)]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        people.query
          .filter(_.tmdbId.isDefined)
          .filter(_.tmdbId inSetBind ids)
          .map(p => (p.tmdbId.get -> p.id))
          .result
      }
    }
  }

  def findThingsByExternalIds(
    source: ExternalSource,
    ids: Set[String],
    typ: Option[ThingType]
  ): Future[Seq[(ExternalId, ThingRaw)]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        val baseQuery = source match {
          case ExternalSource.TheMovieDb =>
            externalIds.query.filter(_.tmdbId inSetBind ids)
          case _ =>
            throw new IllegalArgumentException(
              s"Cannot get things by external source = $source"
            )
        }

        baseQuery
          .flatMap(eid => {
            (typ match {
              case Some(t) => eid.thingRaw.filter(_.`type` === t)
              case None    => eid.thingRaw
            }).map(eid -> _)
          })
          .result
      }
    }
  }

  def findThingsByTmdbIds(
    source: ExternalSource,
    ids: Set[String],
    typ: Option[ThingType]
  ): Future[Map[(String, ThingType), ThingRaw]] = {
    if (ids.isEmpty) {
      Future.successful(Map.empty)
    } else {
      val baseQuery = source match {
        case ExternalSource.TheMovieDb =>
          things.rawQuery
            .filter(_.tmdbId.isDefined)
            .filter(_.tmdbId inSetBind ids)
        case _ =>
          throw new IllegalArgumentException(
            s"Cannot get things by external source = $source"
          )
      }

      val withTypeFilter = typ match {
        case Some(t) => baseQuery.filter(_.`type` === t)
        case None    => baseQuery
      }

      run {
        withTypeFilter.result
      }.map(_.map(thing => (thing.tmdbId.get, thing.`type`) -> thing).toMap)
    }
  }

  def findRawThingsByExternalIds(
    source: ExternalSource,
    ids: Set[String],
    typ: Option[ThingType]
  ): Future[Seq[(ExternalId, ThingRaw)]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        val baseQuery = source match {
          case ExternalSource.TheMovieDb =>
            externalIds.query.filter(_.tmdbId inSetBind ids)
          case _ =>
            throw new IllegalArgumentException(
              s"Cannot get things by external source = $source"
            )
        }

        baseQuery
          .flatMap(eid => {
            (typ match {
              case Some(t) => eid.thingRaw.filter(_.`type` === t)
              case None    => eid.thingRaw
            }).map(eid -> _)
          })
          .result
      }
    }
  }

  def findTmdbGenres(ids: Set[Int]): Future[Seq[Genre]] = {
    if (ids.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        genreReferences.query
          .filter(_.externalSource === ExternalSource.TheMovieDb)
          .filter(_.externalId inSetBind ids.map(_.toString))
          .flatMap(_.genre)
          .result
      }
    }
  }

  implicit object SetUUID extends SetParameter[UUID] {
    def apply(
      v: UUID,
      pp: PositionedParameters
    ): Unit = {
      pp.setObject(v, JDBCType.BINARY.getVendorTypeNumber)
    }
  }

  def upsertAndGetExternalIdPair(
    source: ExternalSource,
    thingId: UUID,
    sourceId: String
  ): Future[UUID] = {
    run {
      sql"""
      INSERT INTO "external_ids" (thing_id, tmdb_id) VALUES ($thingId, $sourceId)
        ON CONFLICT ON CONSTRAINT unique_external_ids_thing_tmdb DO UPDATE SET tmdb_id=EXCLUDED.tmdb_id
        RETURNING (thing_id);
    """.as[String]
    }.map(_.head).map(UUID.fromString(_))
  }

  def saveThing(
    thing: ThingLike,
    externalPair: Option[(ExternalSource, String)] = None
  ): Future[ThingLike] = {
    thing match {
      case raw: ThingRaw =>
        saveThingRaw(raw, externalPair)

      case person: Person =>
        savePerson(person, externalPair)
    }
  }

  def saveThingRaw(
    thing: ThingRaw,
    externalPair: Option[(ExternalSource, String)] = None
  ): Future[ThingRaw] = {
    val thingToInsert = externalPair match {
      case Some((source, id)) if source == ExternalSource.TheMovieDb =>
        if (thing.tmdbId.isEmpty) {
          thing.copy(tmdbId = Some(id))
        } else {
          require(thing.tmdbId.get == id)
          thing
        }

      case _ => thing
    }

    def findByExternalId: Future[Option[ThingRaw]] = {
      externalPair match {
        case _ if thing.tmdbId.isDefined =>
          run {
            things.rawQuery
              .filter(_.tmdbId === thing.tmdbId.get)
              .filter(_.`type` === thing.`type`)
              .take(1)
              .result
              .headOption
          }

        case Some((source, id)) if source == ExternalSource.TheMovieDb =>
          run {
            things.rawQuery
              .filter(_.tmdbId === id)
              .filter(_.`type` === thing.`type`)
              .take(1)
              .result
              .headOption
          }

        case _ =>
          run {
            things.rawQuery
              .filter(_.normalizedName === thing.normalizedName)
              .filter(_.`type` === thing.`type`)
              .result
              .headOption
          }
      }
    }

    def update(existingThing: ThingRaw): Future[ThingRaw] = {
      val updated = thingToInsert.copy(
        id = existingThing.id,
        createdAt = existingThing.createdAt,
        genres = thingToInsert.genres.orElse(existingThing.genres)
      )

      run {
        things.rawQuery
          .filter(t => t.id === existingThing.id)
          .update(updated)
          .map(_ => updated)
      }
    }

    findByExternalId.flatMap {
      case None =>
        run(things.rawQuery += thingToInsert).map(_ => thing).recoverWith {
          case NonFatal(_: PSQLException) =>
            findByExternalId.flatMap {
              case None =>
                throw new IllegalStateException(
                  "Received duplicate key exception, but could not find existing value"
                )
              case Some(existing) =>
                update(existing)
            }
        }

      case Some(e) =>
        update(e)
    }
  }

  def savePerson(
    thing: Person,
    externalPair: Option[(ExternalSource, String)] = None
  ): Future[Person] = {
    val thingToInsert = externalPair match {
      case Some((source, id))
          if source == ExternalSource.TheMovieDb && thing.tmdbId.isEmpty =>
        thing.copy(tmdbId = Some(id))

      case _ => thing
    }

    def findByExternalId: Future[Option[Person]] = {
      externalPair match {
        case _ if thing.tmdbId.isDefined =>
          run {
            people.query
              .filter(_.tmdbId === thing.tmdbId.get)
              .take(1)
              .result
              .headOption
          }

        case Some((source, id)) if source == ExternalSource.TheMovieDb =>
          run {
            people.query.filter(_.tmdbId === id).take(1).result.headOption
          }

        case _ =>
          run {
            people.query
              .filter(_.normalizedName === thing.normalizedName)
              .result
              .headOption
          }
      }
    }

    def update(existingThing: Person): Future[Person] = {
      val updated = thingToInsert.copy(
        id = existingThing.id,
        createdAt = existingThing.createdAt
      )

      run {
        people.query
          .filter(t => t.id === existingThing.id)
          .update(updated)
          .map(_ => updated)
      }
    }

    findByExternalId.flatMap {
      case None =>
        run(people.query += thingToInsert).map(_ => thing).recoverWith {
          case NonFatal(x: PSQLException) =>
            findByExternalId.flatMap {
              case None =>
                throw new IllegalStateException(
                  s"Received duplicate key exception, but could not find existing value. $thingToInsert",
                  x
                )
              case Some(existing) =>
                update(existing)
            }
        }

      case Some(e) =>
        update(e)
    }
  }

  def findExternalIdsByTmdbId(tmdbId: String): Future[Option[ExternalId]] = {
    run {
      externalIds.query.filter(_.tmdbId === tmdbId).result.headOption
    }
  }

  def findExternalIds(thingId: UUID): Future[Option[ExternalId]] = {
    run {
      externalIds.query.filter(_.thingId === thingId).result.headOption
    }
  }

  def upsertPersonThing(personThing: PersonThing): Future[PersonThing] = {
    run {
      personThings.query.insertOrUpdate(personThing).map(_ => personThing)
    }
  }

  def upsertExternalIds(externalId: ExternalId): Future[ExternalId] = {
    val q = if (externalId.thingId.isDefined) {
      Some(externalIds.query.filter(_.thingId === externalId.thingId))
    } else if (externalId.tvEpisodeId.isDefined) {
      Some(externalIds.query.filter(_.tvEpisodeId === externalId.tvEpisodeId))
    } else {
      None
    }

    q.map(_.result.headOption)
      .map(_.flatMap {
        case None =>
          (externalIds.query returning
            externalIds.query.map(_.id) into
            ((eid, id) => eid.copy(id = Some(id)))) += externalId

        case Some(e) =>
          val updated = externalId.copy(id = e.id)
          externalIds.query
            .filter(_.id === e.id)
            .update(updated)
            .map(_ => updated)
      })
      .map(run(_))
      .getOrElse(
        Future.failed(
          new IllegalArgumentException(
            "externalId had neither thingId nor tvEpisodeId defined"
          )
        )
      )
  }

  def getAllGenres(typ: Option[GenreType]): Future[Seq[Genre]] = {
    run {
      val q = genres.query
      val filtered = if (typ.isDefined) q.filter(_.`type` @> typ.toList) else q
      filtered.result
    }
  }

  def saveGenreAssociation(thingGenre: ThingGenre): Future[Int] = {
    run {
      thingGenres.query.insertOrUpdate(thingGenre)
    }
  }

  def findAvailability(
    thingId: UUID,
    networkId: Int
  ): Future[Seq[Availability]] = {
    run {
      availabilities.query
        .filter(_.thingId === thingId)
        .filter(_.networkId === networkId)
        .result
    }
  }

  def findRecentAvailability(
    daysOut: Int,
    networkIds: Option[Set[Int]],
    selectFields: Option[List[Field]]
  ): Future[RecentAvailability] = {
    val recentAvailabilityFut = findPastAvailability(daysOut, networkIds)
    val futureAvailabilityFut =
      findFutureAvailability(daysOut, networkIds, selectFields)

    for {
      recentAvailability <- recentAvailabilityFut
      futureAvailability <- futureAvailabilityFut
    } yield {
      val recent = recentAvailability.map {
        case (av, thing) =>
          av.toDetailed.withThing(
            thing.selectFields(selectFields, defaultFields).toPartial
          )
      }

      RecentAvailability(
        recentlyAdded = recent,
        future = futureAvailability
      )
    }
  }

  def findFutureAvailability(
    daysOut: Int,
    networkIds: Option[Set[Int]],
    selectFields: Option[List[Field]]
  ): Future[FutureAvailability] = {
    val upcomingFut = findUpcomingAvailability(daysOut, networkIds)
    val expiringFut = findExpiringAvailability(daysOut, networkIds)

    for {
      upcoming <- upcomingFut
      expiring <- expiringFut
    } yield {
      FutureAvailability(
        upcoming.map {
          case (av, thing) =>
            av.toDetailed.withThing(
              thing.selectFields(selectFields, defaultFields).toPartial
            )
        },
        expiring.map {
          case (av, thing) =>
            av.toDetailed.withThing(
              thing.selectFields(selectFields, defaultFields).toPartial
            )
        }
      )
    }
  }

  def findPastAvailability(
    daysBack: Int,
    networkIds: Option[Set[Int]]
  ): Future[Seq[(Availability, ThingRaw)]] = {
    val today = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC)
    val daysAgoDate = today.minusDays(daysBack)

    queryAvailabilities(
      today,
      pastStartDate = Some(daysAgoDate),
      futureStartDate = None,
      futureEndDate = None,
      networkIds = networkIds
    )
  }

  def findUpcomingAvailability(
    daysOut: Int,
    networkIds: Option[Set[Int]]
  ): Future[Seq[(Availability, ThingRaw)]] = {
    val today = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC)
    val daysOutDate = today.plusDays(daysOut)

    queryAvailabilities(
      today,
      pastStartDate = None,
      futureStartDate = Some(daysOutDate),
      futureEndDate = None,
      networkIds = networkIds
    )
  }

  def findExpiringAvailability(
    daysOut: Int,
    networkIds: Option[Set[Int]]
  ): Future[Seq[(Availability, ThingRaw)]] = {
    val today = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC)
    val daysOutDate = today.plusDays(daysOut)

    queryAvailabilities(
      today,
      pastStartDate = None,
      futureStartDate = None,
      futureEndDate = Some(daysOutDate),
      networkIds = networkIds
    )
  }

  def findExpiredAvailabilities(): Future[Seq[Availability]] = {
    val pst = ZoneId.of(ZoneId.SHORT_IDS.get("PST"))
    val today = LocalDate
      .now(pst)
      .atStartOfDay()
      .atOffset(pst.getRules.getOffset(Instant.now))

    run {
      availabilities.query
        .filter(_.endDate < today)
        .filter(_.isAvailable)
        .result
    }
  }

  def markAvailabilities(
    ids: Set[Int],
    isAvailable: Boolean
  ): Future[Int] = {
    run {
      availabilities.query
        .filter(_.id inSetBind ids)
        .map(_.isAvailable)
        .update(isAvailable)
    }
  }

  private def queryAvailabilities(
    today: OffsetDateTime,
    pastStartDate: Option[OffsetDateTime],
    futureStartDate: Option[OffsetDateTime],
    futureEndDate: Option[OffsetDateTime],
    networkIds: Option[Set[Int]]
  ): Future[Seq[(Availability, ThingRaw)]] = {
    if (futureEndDate.isEmpty) {
      require(
        pastStartDate.isDefined ^ futureStartDate.isDefined
      )
    }

    val baseQuery = availabilities.query

    val withStart = if (pastStartDate.isDefined) {
      baseQuery.filter(av => {
        av.startDate < today && av.startDate >= pastStartDate.get
      })
    } else if (futureStartDate.isDefined) {
      baseQuery.filter(av => {
        av.startDate > today && av.startDate <= futureStartDate.get
      })
    } else {
      baseQuery
    }

    val withEnd = futureEndDate
      .map(end => {
        withStart.filter(av => {
          av.endDate > today && av.endDate <= end
        })
      })
      .getOrElse(withStart)

    val withNetwork = networkIds
      .map(nids => {
        withEnd.filter(_.networkId inSetBind nids)
      })
      .getOrElse(withEnd)

    run {
      (for {
        avs <- withNetwork
        thing <- things.rawQuery if avs.thingId === thing.id
      } yield {
        avs -> thing
      }).result
    }
  }

  def insertAvailability(av: Availability): Future[Option[Availability]] = {
    insertAvailabilities(Seq(av)).map(_.headOption)
  }

  def insertAvailabilities(
    avs: Seq[Availability]
  ): Future[Seq[Availability]] = {
    run {
      (availabilities.query returning
        availabilities.query.map(_.id) into
        ((av, id) => av.copy(id = Some(id)))) ++= avs
    }
  }

  def saveAvailabilities(avs: Seq[Availability]): Future[Unit] = {
    if (avs.isEmpty) {
      Future.unit
    } else {
      val thingAvailabilities =
        avs.filter(_.thingId.isDefined).groupBy(_.thingId.get)

      val tvEpisodeAvailabilities =
        avs.filter(_.tvShowEpisodeId.isDefined).groupBy(_.tvShowEpisodeId.get)

      def findInsertsAndUpdates(
        incoming: List[Availability],
        existing: Set[Availability]
      ): (Set[Availability], Set[Availability]) = {
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
                case Some(y) =>
                  findInsertsAndUpdates0(
                    xs,
                    remaining - y,
                    updates + x.copy(id = y.id),
                    inserts
                  )
                case None =>
                  findInsertsAndUpdates0(xs, remaining, updates, inserts + x)
              }
          }
        }

        findInsertsAndUpdates0(incoming)
      }

      val thingAvailabilitySave = if (thingAvailabilities.nonEmpty) {
        run {
          availabilities.query
            .filter(_.thingId inSetBind thingAvailabilities.keySet)
            .result
        }.flatMap(existing => {
          val existingByThingId =
            existing.filter(_.thingId.isDefined).groupBy(_.thingId.get)
          val (updates, inserts) = thingAvailabilities.foldLeft(
            (Set.empty[Availability], Set.empty[Availability])
          ) {
            case ((u, i), (thingId, toSave)) =>
              val existingForId =
                existingByThingId.getOrElse(thingId, Seq.empty)
              val (i2, u2) =
                findInsertsAndUpdates(toSave.toList, existingForId.toSet)
              (u ++ u2, i ++ i2)
          }

          val updateQueries = updates.toSeq.map(up => {
            availabilities.query
              .filter(
                a =>
                  a.id === up.id && a.thingId === up.thingId && a.offerType === up.offerType && a.networkId === up.networkId
              )
              .update(up)
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
          availabilities.query
            .filter(_.tvShowEpisodeId inSetBind tvEpisodeAvailabilities.keySet)
            .result
        }.flatMap(existing => {
          val existingByThingId =
            existing
              .filter(_.tvShowEpisodeId.isDefined)
              .groupBy(_.tvShowEpisodeId.get)
          val (updates, inserts) = tvEpisodeAvailabilities.foldLeft(
            (Set.empty[Availability], Set.empty[Availability])
          ) {
            case ((u, i), (thingId, toSave)) =>
              val existingForId =
                existingByThingId.getOrElse(thingId, Seq.empty)
              val (i2, u2) =
                findInsertsAndUpdates(toSave.toList, existingForId.toSet)
              (u ++ u2, i ++ i2)
          }

          val updateQueries = updates.toSeq.map(up => {
            availabilities.query
              .filter(
                a =>
                  a.tvShowEpisodeId === up.tvShowEpisodeId && a.offerType === up.offerType && a.networkId === up.networkId
              )
              .update(up)
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

  def getThingUserDetails(
    userId: String,
    thingId: UUID
  ): Future[UserThingDetails] = {
    // Do they track it?
    val listsQuery = trackedListThings.query
      .filter(_.thingId === thingId)
      .flatMap(l => {
        l.list_fk.filter(_.userId === userId)
      })

    // Actions taken on this item
    val tagsQuery = userThingTags.query
      .filter(_.thingId === thingId)
      .filter(_.userId === userId)

    val foundLists = run(listsQuery.result)
    val foundTags = run(tagsQuery.result)

    for {
      lists <- foundLists
      tags <- foundTags
    } yield {
      UserThingDetails(
        belongsToLists = lists.map(_.toFull),
        tags = tags
      )
    }
  }

  def getThingsUserDetails(
    userId: String,
    thingIds: Set[UUID]
  ): Future[Map[UUID, UserThingDetails]] = {
    val lists = trackedListThings.query
      .filter(_.thingId inSetBind thingIds)
      .flatMap(l => {
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

  def findCollectionByTmdbId(id: String): Future[Option[Collection]] = {
    run {
      collections.collectionsQuery
        .filter(_.tmdbId === id)
        .take(1)
        .result
        .headOption
    }
  }

  def insertCollection(collection: Collection): Future[Collection] = {
    run {
      collections.collectionsQuery returning collections.collectionsQuery.map(
        _.id
      ) into ((collection, id) => collection.copy(id = id)) += collection
    }
  }

  def updateCollection(collection: Collection): Future[Int] = {
    run {
      collections.collectionsQuery
        .filter(_.id === collection.id)
        .update(collection)
    }
  }

  def addThingToCollection(
    collectionId: Int,
    thingId: UUID
  ): Future[Option[Int]] = {
    run {
      sql"""
            INSERT INTO "collection_things" (collection_id, thing_id) VALUES ($collectionId, ${thingId.toString}) 
            ON CONFLICT ON CONSTRAINT collection_things_by_collection DO NOTHING
            RETURNING id;
      """.as[Int].headOption
    }
  }

  def findPeopleForThing(
    thingId: UUID,
    relationType: Option[PersonAssociationType]
  ): Future[Seq[(Person, PersonThing)]] = {
    run {
      InhibitFilter(personThings.query)
        .filter(relationType)(typ => _.relationType === typ)
        .query
        .filter(_.thingId === thingId)
        .flatMap(personThing => {
          personThing.person.map(person => (person, personThing))
        })
        .result
    }
  }

  def findThingsForPerson(
    personId: UUID,
    relationType: Option[PersonAssociationType]
  ): Future[Seq[(ThingRaw, PersonThing)]] = {
    run {
      InhibitFilter(personThings.query)
        .filter(relationType)(typ => _.relationType === typ)
        .query
        .filter(_.personId === personId)
        .flatMap(personThing => {
          personThing.thing.map(thing => (thing, personThing))
        })
        .result
    }
  }

  def findPeopleForThings(
    thingIds: Set[UUID],
    relationType: Option[PersonAssociationType]
  ) = {
    run {
      InhibitFilter(personThings.query)
        .filter(relationType)(typ => _.relationType === typ)
        .query
        .filter(_.thingId inSetBind thingIds)
        .groupBy(_.thingId)
        .flatMap {
          case (id, q) =>
            q.flatMap(personThing => {
              personThing.person
                .map(person => (id, person, personThing.characterName))
            })
        }
        .result
    }
  }

  def updateGenresForThing(
    thingId: UUID,
    genreIds: List[Int]
  ) = {
    run {
      things.rawQuery
        .filter(_.id === thingId)
        .map(_.genres)
        .update(Some(genreIds))
    }
  }
}

object UserThingDetails {
  def empty: UserThingDetails = UserThingDetails(Seq())
}

case class UserThingDetails(
  belongsToLists: Seq[TrackedList],
  tags: Seq[UserThingTag] = Seq.empty)

case class RecentAvailability(
  recentlyAdded: Seq[AvailabilityWithDetails],
  future: FutureAvailability)

case class FutureAvailability(
  upcoming: Seq[AvailabilityWithDetails],
  expiring: Seq[AvailabilityWithDetails])

case class SearchOptions(
  rankingMode: SearchRankingMode,
  thingTypeFilter: Option[Set[ThingType]])

object SearchOptions {
  val default = SearchOptions(
    rankingMode = SearchRankingMode.Popularity,
    thingTypeFilter = None
  )
}
