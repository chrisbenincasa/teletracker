package com.teletracker.service.db

import com.teletracker.service.db.model.{ObjectMetadata, Thing, ThingType}
import com.teletracker.service.model.tmdb.{Movie, MultiTypeXor, Person, TvShow}
import com.teletracker.service.util.Slug
import org.joda.time.DateTime
import shapeless.Poly1

object ThingFactory {
  // NOTE this only handles movies, currently.
  def makeThing(raw: MultiTypeXor): Thing = {
    raw.fold(thingMaker)
  }

  def makeThing(movie: Movie): Thing = {
    val now = getNow()
    Thing(
      None,
      movie.title.get,
      Slug(movie.title.get),
      ThingType.Movie,
      now,
      now,
      Some(ObjectMetadata.withTmdbMovie(movie))
    )
  }

  object thingMaker extends Poly1 {
    implicit val atMovie: Case.Aux[Movie, Thing] = at(makeThing)

    implicit val atTvShow: Case.Aux[TvShow, Thing] = at { show =>
      val now = getNow()
      Thing(
        None,
        show.name,
        Slug(show.name),
        ThingType.Show,
        now,
        now,
        None // TODO!
      )
    }

    implicit val atPerson: Case.Aux[Person, Thing] = at { person =>
      val now = getNow()
      Thing(
        None,
        person.name.get,
        Slug(person.name.get),
        ThingType.Person,
        now,
        now,
        None // TODO!
      )
    }
  }

  private def getNow(): DateTime = DateTime.now()
}
