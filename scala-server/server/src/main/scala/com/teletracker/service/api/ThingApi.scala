package com.teletracker.service.api

import com.google.common.cache.{Cache, CacheBuilder}
import com.teletracker.common.db.access.{
  GenresDbAccess,
  ThingsDbAccess,
  UserThingDetails
}
import com.teletracker.common.db.model.{
  ExternalSource,
  PartialThing,
  Person,
  PersonThing,
  ThingCastMember,
  ThingFactory,
  ThingGenre,
  ThingRaw,
  ThingType,
  TrackedListRow
}
import com.teletracker.common.model.tmdb.{
  CastMember,
  Genre,
  Movie,
  PagedResult,
  TvShow
}
import com.teletracker.common.util.GenreCache.GenreMap
import com.teletracker.common.util.{
  GenreCache,
  HasGenreIdOrSlug,
  HasThingIdOrSlug,
  Slug
}
import com.teletracker.service.api.model.{
  Converters,
  EnrichedPerson,
  PersonCredit
}
import com.twitter.util.Stopwatch
import io.circe.Json
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.math.Ordering.OptionOrdering

class ThingApi @Inject()(
  thingsDbAccess: ThingsDbAccess,
  genresDbAccess: GenresDbAccess,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val slugToIdCache: Cache[String, UUID] =
    CacheBuilder.newBuilder().maximumSize(10000).build()
  private val genreSlugToIdCache: Cache[String, UUID] =
    CacheBuilder.newBuilder().maximumSize(1000).build()

  import io.circe.optics.JsonPath._

  private val movieCast =
    root.themoviedb.movie.credits.cast.as[Option[List[CastMember]]]
  private val showCast =
    root.themoviedb.show.credits.cast.as[Option[List[CastMember]]]

  private val movieReleaseDate =
    root.themoviedb.movie.release_date.as[String]
  private val tvReleaseDate =
    root.themoviedb.show.first_air_date.as[String]

  private val movieRecommendations =
    root.themoviedb.movie.recommendations.as[PagedResult[Movie]]
  private val tvRecommendations =
    root.themoviedb.show.recommendations.as[PagedResult[TvShow]]

  private val movieGenres =
    root.themoviedb.movie.genres.as[List[Genre]]
  private val tvGenres =
    root.themoviedb.show.genres.as[List[Genre]]

  private val posterPaths =
    Stream("movie", "show").map(tpe => {
      (j: Json) =>
        j.hcursor
          .downField("themoviedb")
          .downField(tpe)
          .get[Option[String]]("poster_path")
          .toOption
          .flatten
    })

  private case class GetThingIntermediate(
    thing: Option[PartialThing],
    userDetails: UserThingDetails,
    people: Seq[(Person, PersonThing)],
    genres: Seq[ThingGenre])

  def getThing(
    userId: Option[String],
    idOrSlug: String,
    thingType: ThingType
  ): Future[Option[PartialThing]] = {
    def queryViaId(id: UUID) = {
      val thingFut = thingsDbAccess.findThingById(id, thingType)
      val userDetailsFut = userId
        .map(thingsDbAccess.getThingUserDetails(_, id))
        .getOrElse(Future.successful(UserThingDetails.empty))
      val peopleFut = thingsDbAccess.findPeopleForThing(id, None)
      val genresFut = genresDbAccess.findGenresForThing(id)

      for {
        thing <- thingFut
        details <- userDetailsFut
        people <- peopleFut
        genres <- genresFut
      } yield Some(GetThingIntermediate(thing, details, people, genres))
    }

    def queryViaSlug(slug: Slug) = {
      timed("findThingBySlug")(
        thingsDbAccess.findThingBySlug(slug, thingType)
      ).flatMap {
        case None => Future.successful(None)
        case Some(thing) =>
          slugToIdCache.put(slug.value, thing.id)

          val userDetailsFut =
            timed("getThingUserDetails") {
              userId
                .map(thingsDbAccess.getThingUserDetails(_, thing.id))
                .getOrElse(Future.successful(UserThingDetails.empty))
            }
          val peopleFut = timed("findPeopleForThing") {
            thingsDbAccess.findPeopleForThing(thing.id, None)
          }

          val genresFut = genresDbAccess.findGenresForThing(thing.id)

          for {
            details <- userDetailsFut
            people <- peopleFut
            genres <- genresFut
          } yield {
            Some(GetThingIntermediate(Some(thing), details, people, genres))
          }
      }
    }

    val thingAndDetailsFut = HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id) => queryViaId(id)

      case Right(slug) =>
        Option(slugToIdCache.getIfPresent(slug.value)) match {
          case Some(id) => queryViaId(id)
          case None     => queryViaSlug(slug)
        }
    }

    thingAndDetailsFut.flatMap {
      case None | Some(GetThingIntermediate(None, _, _, _)) =>
        Future.successful(None)

      case Some(GetThingIntermediate(Some(thing), details, people, genres)) =>
        val cast = people.map {
          case (person, relation) =>
            ThingCastMember(
              person.id,
              person.normalizedName,
              person.name,
              relation.characterName,
              Some(relation.relationType),
              person.tmdbId,
              person.popularity
            )
        }

        val rawJsonMembers = thing.metadata
          .flatMap(meta => {
            movieCast
              .getOption(meta)
              .orElse(showCast.getOption(meta))
              .flatten
          })

        val rawRecommendations = gatherRecommendations(thing, userId)

        val sanitizedCast = sanitizeCastMembers(rawJsonMembers, cast.toList)

        val baseThing = thing
          .withGenres(genres.map(_.genreId).toSet)
          .withUserMetadata(details)
          .withCast(sanitizedCast)

        rawRecommendations match {
          case Some(recsFut) =>
            recsFut.map(recs => {
              // TODO: Just select the thing without metadata from the server...
              Some(baseThing.withRecommendations(recs.map {
                case (thing, belongsToLists) =>
                  thing.toPartial.withUserMetadata(
                    UserThingDetails(
                      belongsToLists = belongsToLists.map(_.toFull)
                    )
                  )
              }))
            })
          case None =>
            Future.successful(Some(baseThing))
        }
    }
  }

  private def gatherRecommendations(
    thing: PartialThing,
    userId: Option[String]
  ): Option[Future[List[(ThingRaw, Seq[TrackedListRow])]]] = {
    thing.metadata
      .flatMap(movieRecommendations.getOption)
      .map(movies => {
        val ids = movies.results.map(_.id.toString).take(6)
        timed("gatherThings") {
          gatherThings(ids, ThingType.Movie, userId)
        }
      })
      .orElse {
        thing.metadata
          .flatMap(tvRecommendations.getOption)
          .map(shows => {
            val ids = shows.results.map(_.id.toString).take(6)
            gatherThings(ids, ThingType.Show, userId)
          })
      }
  }

  private def timed[T](op: String)(f: => Future[T]): Future[T] = {
    val s = Stopwatch.start()
    val ret = f
    f.onComplete(_ => {
      logger.debug(s"op: $op took ${s().inMillis} ms")
    })
    ret
  }

  private def gatherThings(
    ids: List[String],
    thingType: ThingType,
    userId: Option[String]
  ): Future[List[(ThingRaw, Seq[TrackedListRow])]] = {
    val idsByOrder = ids.zipWithIndex.toMap
    thingsDbAccess
      .findThingsByTmdbIds(
        ExternalSource.TheMovieDb,
        ids.toSet,
        Some(thingType),
        userId
      )
      .map(results => {
        results.toList
          .map {
            case ((tmdbId, _), thingAndDetails) => tmdbId -> thingAndDetails
          }
          .sortBy {
            case (tmdbId, _) => idsByOrder(tmdbId)
          }
          .map(_._2)
      })
  }

  private def sanitizeCastMembers(
    rawJsonMembers: Option[List[CastMember]],
    thingCastMember: List[ThingCastMember]
  ): List[ThingCastMember] = {
    rawJsonMembers match {
      case None => thingCastMember
      case Some(rawMembers) => {
        val rawMemberById =
          rawMembers.map(member => member.id.toString -> member).toMap

        val orderById = rawMembers
          .map(
            member => member.id.toString -> member.order.getOrElse(Int.MaxValue)
          )
          .toMap

        thingCastMember
          .map(member => {
            val rawMember = member.tmdbId.flatMap(rawMemberById.get)
            member
              .withOrder(member.tmdbId.flatMap(orderById.get))
              .withProfilePath(rawMember.flatMap(_.profile_path))
          })
          .sortBy(_.order)(NullsLastOrdering)
      }
    }
  }

  implicit private val LocalDateOrdering: Ordering[LocalDate] =
    Ordering.fromLessThan(_.isBefore(_))

  private def NullsLastOrdering[T](
    implicit ord: Ordering[T]
  ): Ordering[Option[T]] = new OptionOrdering[T] {
    override def optionOrdering: Ordering[T] = ord
    override def compare(
      x: Option[T],
      y: Option[T]
    ) = (x, y) match {
      case (None, None)       => 0
      case (None, _)          => 1
      case (_, None)          => -1
      case (Some(x), Some(y)) => optionOrdering.compare(x, y)
    }
  }

  def getPerson(
    userId: Option[String],
    idOrSlug: String
  ): Future[Option[EnrichedPerson]] = {
    val personFut = HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id)    => thingsDbAccess.findPersonById(id)
      case Right(slug) => thingsDbAccess.findPersonBySlug(slug)
    }

    personFut.flatMap {
      case None => Future.successful(None)

      case Some(person) =>
        val genreCacheFut = genreCache.get()

        val relevantThingsFut =
          thingsDbAccess.findThingsForPerson(person.id, None)

        for {
          relevantThings <- relevantThingsFut
          genres <- genreCacheFut
        } yield {
          val credits = relevantThings.map {
            case (thing, relation) =>
              PersonCredit(
                id = thing.id,
                name = thing.name,
                normalizedName = thing.normalizedName,
                tmdbId = thing.tmdbId,
                popularity = thing.popularity,
                `type` = thing.`type`,
                associationType = relation.relationType,
                characterName = relation.characterName,
                releaseDate = extractReleaseDate(thing),
                posterPath = extractPosterPath(thing),
                genreIds = extractGenreIds(thing, genres).toSet
              )
          }

          Some(
            Converters
              .dbPersonToEnrichedPerson(person)
              .withCredits(
                credits.toList
                  .sortBy(_.releaseDate)(
                    NullsLastOrdering[LocalDate].reverse
                  )
              )
          )
        }
    }
  }

  def getPopularByGenre(
    genreIdOrSlug: String,
    thingType: Option[ThingType]
  ): Future[Option[Seq[PartialThing]]] = {
    genreCache
      .get()
      .map(_.values)
      .flatMap(genres => {
        val genre = HasGenreIdOrSlug.parse(genreIdOrSlug) match {
          case Left(id)    => genres.find(_.id.contains(id))
          case Right(slug) => genres.find(_.slug == slug)
        }

        // TODO: Fill in user deets
        genre.map(g => {
          genresDbAccess
            .findMostPopularThingsForGenre(g.id.get, thingType)
            .map(_.map(_.toPartial))
            .map(Some(_))
        })
      }.getOrElse(Future.successful(None)))
  }

  private def extractReleaseDate(thingRaw: ThingRaw) = {
    thingRaw.metadata
      .flatMap(meta => {
        movieReleaseDate.getOption(meta).orElse(tvReleaseDate.getOption(meta))
      })
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_))
  }

  private def extractPosterPath(thingRaw: ThingRaw) = {
    thingRaw.metadata.collectFirst {
      case meta => posterPaths.map(_.apply(meta)).find(_.isDefined).flatten
    }.flatten
  }

  private def extractGenreIds(
    thingRaw: ThingRaw,
    genres: GenreMap
  ) = {
    thingRaw.metadata.toList
      .flatMap(meta => {
        movieGenres.getOption(meta).orElse(tvGenres.getOption(meta))
      })
      .flatMap(tmdbGenres => {
        tmdbGenres
          .map(_.id)
          .flatMap(id => genres.get(ExternalSource.TheMovieDb -> id.toString))
          .flatMap(_.id)
      })
  }
}
