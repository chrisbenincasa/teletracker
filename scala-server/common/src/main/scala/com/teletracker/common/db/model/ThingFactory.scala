package com.teletracker.common.db.model

import com.teletracker.common.model.Watchable
import com.teletracker.common.model.tmdb.{
  MediaType,
  Movie,
  MultiTypeXor,
  PersonCredit,
  TvShow,
  Person => TmdbPerson
}
import com.teletracker.common.util.Slug
import shapeless.{HNil, Poly1, PolyDefns}
import java.time.{LocalDate, OffsetDateTime}
import com.teletracker.common.util.Movies._
import com.teletracker.common.util.Shows._
import com.teletracker.common.util.People._
import io.circe.syntax._
import java.util.UUID
import com.teletracker.common.util.TheMovieDb._
import scala.util.{Failure, Try}

object ThingFactory {
  // NOTE this only handles movies, currently.
  def makeThing(raw: MultiTypeXor): Try[ThingLike] = {
    raw.fold(thingMaker)
  }

  def makeThingGen[T](
    thing: T
  )(implicit caseExists: PolyDefns.Case1.Aux[thingMaker.type, T, Try[ThingLike]]
  ): Try[ThingLike] = {
    caseExists.apply(thing :: HNil)
  }

  def toWatchableThing[T](
    thing: T
  )(implicit watchable: Watchable[T]
  ): Try[ThingRaw] = {
    watchable.toThingRaw(thing)
  }

  def makeThing(
    credit: PersonCredit,
    thingType: ThingType
  ): Try[ThingRaw] = {
    Try {
      val releaseYear = credit.release_date
        .filter(_.nonEmpty)
        .map(LocalDate.parse(_).getYear)

      require(
        credit.title.isDefined || credit.name.isDefined || credit.original_title.isDefined,
        s"Cannot save credit for ${thingType} id = ${credit.id} because it has no title"
      )

      val chosenName =
        credit.name.orElse(credit.title).orElse(credit.original_title).get

      require(
        releaseYear.isDefined,
        s"Cannot save credit for ${thingType} id = ${credit.id} = $chosenName because it has no release year"
      )

      val metadata = thingType match {
        case ThingType.Movie =>
          Some(ObjectMetadata.withTmdbMovie(credit.asMovie))
        case ThingType.Show =>
          Some(ObjectMetadata.withTmdbShow(credit.asTvShow))
        case _ => None
      }

      val now = getNow()

      ThingRawFactory.forObjectMetadata(
        UUID.randomUUID(),
        chosenName,
        Slug(chosenName, releaseYear.get),
        thingType,
        now,
        now,
        metadata,
        Some(credit.id.toString),
        credit.popularity,
        None
      )
    }
  }

  def makeThing(movie: Movie): Try[ThingRaw] = {
    Try {
      val releaseYear = movie.releaseYear

      require(
        movie.title.isDefined,
        s"Cannot save movie ${movie.id} because it has no title"
      )
      require(
        releaseYear.isDefined,
        s"Cannot save movie ${movie.id} = ${movie.title.get} because it has no release year"
      )

      val now = getNow()
      ThingRawFactory.forObjectMetadata(
        UUID.randomUUID(),
        movie.title.get,
        Slug(movie.title.get, releaseYear.get),
        ThingType.Movie,
        now,
        now,
        Some(ObjectMetadata.withTmdbMovie(movie)),
        Some(movie.id.toString),
        movie.popularity,
        None
      )
    }
  }

  def makeThing(show: TvShow): Try[ThingRaw] = {
    Try {
      val now = getNow()
      val releaseYear = show.releaseYear

      require(
        releaseYear.isDefined,
        s"Attempted to get release year from show = ${show.id} but couldn't: (original field = ${show.first_air_date})"
      )

      ThingRawFactory.forObjectMetadata(
        UUID.randomUUID(),
        show.name,
        Slug(show.name, releaseYear.get),
        ThingType.Show,
        now,
        now,
        Some(ObjectMetadata.withTmdbShow(show)),
        Some(show.id.toString),
        show.popularity,
        None
      )
    }
  }

  def makeThing(person: TmdbPerson): Try[ThingLike] = {
    Try {
      val now = getNow()
      Person(
        UUID.randomUUID(),
        person.name.get,
        Slug(person.name.get, person.releaseYear),
        now,
        now,
        Some(person.asJson),
        Some(person.id.toString),
        person.popularity
      )
    }
  }

//  private def makePersonFromCastMember(castMember: CastMember): Try[Thing] = {
//    Try {
//      val now = getNow()
//      Thing(
//        UUID.randomUUID(),
//        castMember.name.get,
//        Slug(castMember.name.get, castMember.releaseYear.get),
//        ThingType.Person,
//        now,
//        now,
//        Some(ObjectMetadata.withTmdbPerson(person)),
//        Some(person.id.toString),
//        person.popularity
//      )
//    }
//  }

  object thingMaker extends Poly1 {
    implicit val atMovie: Case.Aux[Movie, Try[ThingLike]] = at(makeThing)

    implicit val atTvShow: Case.Aux[TvShow, Try[ThingLike]] = at(makeThing)

    implicit val atPerson: Case.Aux[TmdbPerson, Try[ThingLike]] = at(makeThing)

    implicit val atPersonCredit: Case.Aux[PersonCredit, Try[ThingLike]] = at {
      credit =>
        credit.media_type match {
          case Some(MediaType.Movie) => makeThing(credit, ThingType.Movie)
          case Some(MediaType.Tv)    => makeThing(credit, ThingType.Show)
          case typ =>
            Failure(
              new IllegalArgumentException(s"Unrecognized thingtype = $typ")
            )
        }
    }

//    implicit val atCastMember: Case.Aux[CastMember, Try[Thing]] = at(
//      makePersonFromCastMember
//    )
  }

  private def getNow(): OffsetDateTime = OffsetDateTime.now()
}
