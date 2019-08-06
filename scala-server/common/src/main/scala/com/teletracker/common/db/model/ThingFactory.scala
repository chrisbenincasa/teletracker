package com.teletracker.common.db.model

import com.teletracker.common.model.tmdb.{Movie, MultiTypeXor, Person, TvShow}
import com.teletracker.common.util.Slug
import shapeless.Poly1
import java.time.OffsetDateTime
import com.teletracker.common.util.Movies._
import com.teletracker.common.util.Shows._
import com.teletracker.common.util.People._
import java.util.UUID
import scala.util.Try

object ThingFactory {
  // NOTE this only handles movies, currently.
  def makeThing(raw: MultiTypeXor): Try[Thing] = {
    raw.fold(thingMaker)
  }

  def makeThing(movie: Movie): Try[Thing] = {
    Try {
      val releaseYear = movie.releaseYear

      require(movie.title.isDefined)
      require(releaseYear.isDefined)

      val now = getNow()
      Thing(
        UUID.randomUUID(),
        movie.title.get,
        Slug(movie.title.get, releaseYear.get),
        ThingType.Movie,
        now,
        now,
        Some(ObjectMetadata.withTmdbMovie(movie))
      )
    }
  }

  def makeThing(show: TvShow): Try[Thing] = {
    Try {
      val now = getNow()
      val releaseYear = show.releaseYear

      require(
        releaseYear.isDefined,
        s"Attempted to get release year from ${show.releaseYear}"
      )

      Thing(
        UUID.randomUUID(),
        show.name,
        Slug(show.name, releaseYear.get),
        ThingType.Show,
        now,
        now,
        Some(ObjectMetadata.withTmdbShow(show))
      )
    }
  }

  def makePerson(person: Person): Try[Thing] = {
    Try {
      val now = getNow()
      Thing(
        UUID.randomUUID(),
        person.name.get,
        Slug(person.name.get, person.releaseYear.get),
        ThingType.Person,
        now,
        now,
        Some(ObjectMetadata.withTmdbPerson(person))
      )
    }
  }

  object thingMaker extends Poly1 {
    implicit val atMovie: Case.Aux[Movie, Try[Thing]] = at(makeThing)

    implicit val atTvShow: Case.Aux[TvShow, Try[Thing]] = at(makeThing)

    implicit val atPerson: Case.Aux[Person, Try[Thing]] = at(makePerson)
  }

  private def getNow(): OffsetDateTime = OffsetDateTime.now()
}
