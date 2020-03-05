package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.model.tmdb.{Genre, Movie, Person, TvShow}

object FieldExtractors {
  object extractId extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, String] = at { _.id.toString }
    implicit val atShow: Case.Aux[TvShow, String] = at { _.id.toString }
    implicit val atPerson: Case.Aux[Person, String] = at { _.id.toString }
  }

  object extractGenres extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, Option[List[Genre]]] = at { _.genres }
    implicit val atShow: Case.Aux[TvShow, Option[List[Genre]]] = at { _.genres }
    implicit val atPerson: Case.Aux[Person, Option[List[Genre]]] = at { _ =>
      None
    }
  }

  object extractGenreIds extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, Option[List[Int]]] = at {
      _.genre_ids
    }
    implicit val atShow: Case.Aux[TvShow, Option[List[Int]]] = at {
      _.genre_ids
    }
    implicit val atPerson: Case.Aux[Person, Option[List[Genre]]] = at { _ =>
      None
    }
  }

  object extractType extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, ThingType] = at { _ =>
      ThingType.Movie
    }
    implicit val atShow: Case.Aux[TvShow, ThingType] = at { _ =>
      ThingType.Show
    }
    implicit val atPerson: Case.Aux[Person, ThingType] = at { _ =>
      ThingType.Person
    }
  }
}
