package com.teletracker.common.model.tmdb

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.util.{Movies, Shows}

trait TmdbWatchable[T] {
  def id(x: T): Int
  def title(x: T): Option[String]
  def releaseYear(x: T): Option[Int]
  def mediaType(x: T): ThingType

  def asMovie(x: T): Option[Movie]
  def asShow(x: T): Option[TvShow]
}

object TmdbWatchable {
  implicit val MovieIsWatchable = new TmdbWatchable[Movie] {
    override def id(x: Movie): Int = x.id

    override def title(x: Movie): Option[String] =
      x.title.orElse(x.original_title)

    override def releaseYear(x: Movie): Option[Int] =
      Movies.toRichMovie(x).releaseYear

    override def mediaType(x: Movie): ThingType = ThingType.Movie

    override def asMovie(x: Movie): Option[Movie] = Some(x)

    override def asShow(x: Movie): Option[TvShow] = None
  }

  implicit val TvShowIsWatchable = new TmdbWatchable[TvShow] {
    override def id(x: TvShow): Int = x.id

    override def title(x: TvShow): Option[String] =
      Option(x.name).filter(_.nonEmpty).orElse(x.original_name)

    override def releaseYear(x: TvShow): Option[Int] =
      Shows.toRichShow(x).releaseYear

    override def mediaType(x: TvShow): ThingType = ThingType.Show

    override def asMovie(x: TvShow): Option[Movie] = None

    override def asShow(x: TvShow): Option[TvShow] = Some(x)
  }

  implicit val MovieOrTvShowIsWatchable =
    new TmdbWatchable[Either[Movie, TvShow]] {
      override def id(x: Either[Movie, TvShow]): Int = x.fold(_.id, _.id)
      override def title(x: Either[Movie, TvShow]): Option[String] =
        x.fold(MovieIsWatchable.title, TvShowIsWatchable.title)
      override def releaseYear(x: Either[Movie, TvShow]): Option[Int] =
        x.fold(MovieIsWatchable.releaseYear, TvShowIsWatchable.releaseYear)
      override def mediaType(x: Either[Movie, TvShow]): ThingType =
        x.fold(_ => ThingType.Movie, _ => ThingType.Show)
      override def asMovie(x: Either[Movie, TvShow]): Option[Movie] =
        x.fold(MovieIsWatchable.asMovie, TvShowIsWatchable.asMovie)
      override def asShow(x: Either[Movie, TvShow]): Option[TvShow] =
        x.fold(MovieIsWatchable.asShow, TvShowIsWatchable.asShow)
    }
}
