package com.teletracker.common.util

import com.teletracker.common.model.tmdb.{Genre, Movie, PersonCredit, TvShow}

object TheMovieDb {
  implicit def toRichPersonCredits(pc: PersonCredit): RichPersonCredits =
    new RichPersonCredits(pc)
}

final class RichPersonCredits(val pc: PersonCredit) extends AnyVal {
  def asMovie: Movie = {
    Movie(
      adult = pc.adult,
      backdrop_path = pc.backdrop_path,
      belongs_to_collection = None,
      budget = None,
      genres = None,
      genre_ids = pc.genre_ids,
      homepage = None,
      id = pc.id,
      imdb_id = None,
      original_language = pc.original_language,
      original_title = pc.original_title,
      overview = pc.overview,
      popularity = pc.popularity,
      poster_path = pc.poster_path,
      production_companies = None,
      release_date = pc.release_date,
      revenue = None,
      runtime = None,
      status = None,
      tagline = None,
      title = pc.title,
      video = pc.video,
      vote_average = pc.vote_average,
      vote_count = pc.vote_count,
      release_dates = None,
      credits = None,
      external_ids = None,
      recommendations = None,
      similar = None
    )
  }

  def asTvShow: TvShow = {
    TvShow(
      backdrop_path = pc.backdrop_path,
      created_by = None,
      episode_run_time = None,
      first_air_date = None,
      genres = pc.genre_ids.map(_.map(id => Genre(id, ""))), // TODO: Is this the right thing to do
      homepage = None,
      id = pc.id,
      in_production = None,
      languages = None,
      last_air_date = None,
      name = pc.name.orElse(pc.title).get,
      networks = None,
      number_of_episodes = None,
      number_of_seasons = None,
      origin_country = None,
      original_language = pc.original_language,
      original_name = pc.original_title,
      overview = pc.overview,
      popularity = pc.popularity,
      poster_path = pc.poster_path,
      seasons = None,
      status = None,
      `type` = None,
      vote_average = pc.vote_average,
      vote_count = pc.vote_count,
      content_ratings = None,
      credits = None,
      external_ids = None,
      recommendations = None,
      similar = None
    )
  }
}
