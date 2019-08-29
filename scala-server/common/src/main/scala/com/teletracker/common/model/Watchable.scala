package com.teletracker.common.model

import com.teletracker.common.db.model.{ThingFactory, ThingRaw, ThingType}
import com.teletracker.common.model.tmdb.{
  MediaType,
  Movie,
  PersonCredit,
  TvShow
}
import shapeless.{:+:, CNil, Inl, Inr}
import scala.util.{Failure, Try}

trait Watchable[T] {
  def toThingRaw(t: T): Try[ThingRaw]
}

object Watchable {
  implicit val personCreditIsWatchable = new Watchable[PersonCredit] {
    override def toThingRaw(t: PersonCredit): Try[ThingRaw] = {
      t.media_type match {
        case Some(MediaType.Movie) => ThingFactory.makeThing(t, ThingType.Movie)
        case Some(MediaType.Tv)    => ThingFactory.makeThing(t, ThingType.Show)
        case typ =>
          Failure(
            new IllegalArgumentException(s"Unrecognized thingtype = $typ")
          )
      }
    }
  }

  implicit val movieIsWatchable = new Watchable[Movie] {
    override def toThingRaw(t: Movie): Try[ThingRaw] = ThingFactory.makeThing(t)
  }

  implicit val tvShowIsWatchable = new Watchable[TvShow] {
    override def toThingRaw(t: TvShow): Try[ThingRaw] =
      ThingFactory.makeThing(t)
  }

  implicit val movieOrTvShowIsWatchable =
    new Watchable[Movie :+: TvShow :+: CNil] {
      override def toThingRaw(t: Movie :+: TvShow :+: CNil): Try[ThingRaw] = {
        t match {
          case Inl(movie)     => ThingFactory.makeThing(movie)
          case Inr(Inl(show)) => ThingFactory.makeThing(show)
          case _              => ???
        }
      }
    }
}
