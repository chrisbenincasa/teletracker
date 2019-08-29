package com.teletracker.common.model

import com.teletracker.common.db.model.{ThingFactory, ThingLike}
import com.teletracker.common.model.tmdb.{Movie, TvShow, Person => TmdbPerson}
import scala.util.Try

trait Thingable[T] {
  def toThing(t: T): Try[ThingLike]
}

object Thingable {
  implicit val movieIsThingLike = new Thingable[Movie] {
    override def toThing(t: Movie): Try[ThingLike] = ThingFactory.makeThing(t)
  }

  implicit val tvShowIsThingLike = new Thingable[TvShow] {
    override def toThing(t: TvShow): Try[ThingLike] = ThingFactory.makeThing(t)
  }

  implicit val personIsThingLike = new Thingable[TmdbPerson] {
    override def toThing(t: TmdbPerson): Try[ThingLike] =
      ThingFactory.makeThing(t)
  }
}
