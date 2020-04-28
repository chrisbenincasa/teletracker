package com.teletracker.common.model.tmdb

import io.circe.generic.JsonCodec
import io.circe._
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.teletracker.common.util.json.circe._
import shapeless.{:+:, CNil}

trait TvShowId

@JsonCodec case class TvShow(
  backdrop_path: Option[String],
  created_by: Option[List[TvShowCreatedBy]],
  episode_run_time: Option[List[Int]],
  first_air_date: Option[String],
  genres: Option[List[Genre]],
  genre_ids: Option[List[Int]],
  homepage: Option[String],
  id: Int,
  in_production: Option[Boolean],
  languages: Option[List[String]],
  last_air_date: Option[String],
  name: String,
  networks: Option[List[TvNetwork]],
  number_of_episodes: Option[Int],
  number_of_seasons: Option[Int],
  origin_country: Option[List[String]],
  original_language: Option[String],
  original_name: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  poster_path: Option[String],
  seasons: Option[List[TvShowSeason]],
  status: Option[String],
  `type`: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  // Join fields
  alternative_titles: Option[TvAlternativeTitles],
  content_ratings: Option[TvContentRatings],
  credits: Option[TvShowCredits],
  external_ids: Option[ExternalIds],
  recommendations: Option[PagedResult[TvShow]],
  similar: Option[PagedResult[TvShow]],
  videos: Option[TvShowVideos],
  translations: Option[TvShowTranslations],
  images: Option[TvShowImages])
    extends HasTmdbId

@JsonCodec case class TvAlternativeTitles(results: List[TvAlternativeTitle])

@JsonCodec case class TvAlternativeTitle(
  iso_3166_1: String,
  title: String,
  `type`: Option[String])

@JsonCodec case class TvNetwork(
  id: Int,
  name: String)

@JsonCodec case class TvShowSeason(
  air_date: Option[String],
  episode_count: Option[Int],
  episodes: Option[List[TvShowEpisode]],
  id: Int,
  poster_path: Option[String],
  season_number: Option[Int],
  name: Option[String],
  overview: Option[String],
  // Appended joins
  credits: Option[TvShowCredits],
  external_ids: Option[ExternalIds],
  videos: Option[List[TvShowVideo]])

@JsonCodec case class TvShowEpisode(
  air_date: Option[String],
  credits: Option[TvShowCredits],
  episode_number: Option[Int],
  external_ids: Option[ExternalIds],
  name: Option[String],
  overview: Option[String],
  id: Int,
  images: Option[TvShowEpisodeImages],
  production_code: Option[String],
  season_number: Option[Int],
  still_path: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  videos: Option[List[TvShowVideo]])

@JsonCodec case class TvShowTranslations(
  translations: Option[List[MovieTranslation]])

@JsonCodec case class TvShowTranslation(
  iso_3166_1: Option[String],
  iso_639_1: Option[String],
  name: Option[String],
  english_name: Option[String],
  data: Option[MovieTranslationData])

@JsonCodec case class TvShowTranslationData(
  name: Option[String],
  overview: Option[String],
  homepage: Option[String])

@JsonCodec case class TvShowEpisodeImages(stills: Option[List[Image]])

@JsonCodec case class TvShowCredits(
  cast: Option[List[CastMember]],
  crew: Option[List[CrewMember]],
  guest_stars: Option[List[CastMember]])

@JsonCodec case class TvShowCreatedBy(
  id: Int,
  name: String,
  gender: Option[Int],
  profile_path: Option[String])

@JsonCodec case class SearchTvShowsRequest(
  query: String,
  language: Option[String],
  page: Option[Int],
  first_air_date_year: Option[Int])

@JsonCodec case class TvExternalIds(
  tvdb_id: Option[Int],
  id: Int)

@JsonCodec case class TvShowVideos(results: List[TvShowVideo])

@JsonCodec case class TvShowVideo(
  id: String,
  iso_639_1: Option[String],
  iso_3166_1: Option[String],
  key: String,
  name: Option[String],
  site: String,
  size: Option[Int],
  `type`: Option[String])

@JsonCodec case class TvContentRatings(results: List[TvContentRating])

@JsonCodec case class TvContentRating(
  iso_3166_1: String,
  rating: String)

@JsonCodec case class TvShowImages(
  backdrops: Option[List[MovieImage]],
  posters: Option[List[MovieImage]])

@JsonCodec case class TvShowImage(
  aspect_ratio: Option[Double],
  file_path: Option[String],
  height: Option[Int],
  iso_629_1: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  width: Option[Int])
