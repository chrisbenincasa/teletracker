package com.teletracker.common.model.tmdb

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.teletracker.common.util.json.circe._
import shapeless.{:+:, CNil}

sealed trait TmdbQueryableEntity

case class SearchMultiRequest(
  query: String,
  language: Option[String],
  page: Option[Int],
  include_adult: Option[Boolean])

case class MultiSearchResponseFields(media_type: String)

case class SearchMoviesRequest(
  query: String,
  language: Option[String],
  page: Option[Int],
  include_adult: Option[Boolean],
  region: Option[String],
  year: Option[Int],
  primary_release_year: Option[Int])

// Marker for movie IDs
trait MovieId

@JsonCodec case class BelongsToCollection(
  id: Int,
  name: String,
  poster_path: Option[String],
  backdrop_path: Option[String])

trait HasTmdbId {
  def id: Int
}

@JsonCodec case class Movie(
  adult: Option[Boolean],
  backdrop_path: Option[String],
  belongs_to_collection: Option[BelongsToCollection],
  budget: Option[Long],
  genres: Option[List[Genre]],
  genre_ids: Option[List[Int]],
  homepage: Option[String],
  id: Int,
  imdb_id: Option[String],
  original_language: Option[String],
  original_title: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  poster_path: Option[String],
  production_companies: Option[List[ProductionCompany]],
  release_date: Option[String],
  revenue: Option[Long],
  runtime: Option[Int],
  status: Option[String],
  tagline: Option[String],
  title: Option[String],
  video: Option[Boolean],
  vote_average: Option[Double],
  vote_count: Option[Int],
  // Join fields
  alternative_titles: Option[MovieAlternativeTitles],
  release_dates: Option[MovieReleaseDates],
  credits: Option[MovieCredits],
  external_ids: Option[ExternalIds],
  recommendations: Option[PagedResult[Movie]],
  similar: Option[PagedResult[Movie]],
  videos: Option[MovieVideos])
    extends TmdbQueryableEntity
    with HasTmdbId

@JsonCodec case class MovieAlternativeTitles(
  titles: List[MovieAlternativeTitle])

@JsonCodec case class MovieAlternativeTitle(
  iso_3166_1: String,
  title: String,
  `type`: Option[String])

@JsonCodec case class MovieExternalIds(
  imdb_id: Option[String],
  id: Int)

@JsonCodec case class MovieReleaseDates(results: List[MovieReleaseDate])

@JsonCodec case class MovieReleaseDate(
  iso_3166_1: String,
  release_dates: List[MovieCountryRelease])

@JsonCodec case class MovieCountryRelease(
  certification: Option[String],
  release_date: Option[String],
  `type`: Int // Maps to MovieReleaseType
)

@JsonCodec case class MovieCredits(
  cast: Option[List[CastMember]],
  crew: Option[List[CrewMember]])

@JsonCodec case class MovieVideos(results: List[MovieVideo])

@JsonCodec case class MovieVideo(
  id: String,
  iso_639_1: Option[String],
  iso_3166_1: Option[String],
  key: String,
  name: Option[String],
  site: String,
  size: Option[Int],
  `type`: Option[String])

trait PersonId

@JsonCodec
case class Person(
  adult: Option[Boolean],
//  also_known_as: Option[Map[String, Any]],
  biography: Option[String],
  birthday: Option[String],
  deathday: Option[String],
  gender: Option[Int],
  homepage: Option[String],
  id: Int,
  imdb_id: Option[String],
  name: Option[String],
  place_of_birth: Option[String],
  popularity: Option[Double],
  profile_path: Option[String],
  known_for: Option[List[Movie :+: TvShow :+: CNil]],
  // Join fields
  credits: Option[PersonCredits],
  combined_credits: Option[PersonCredits],
  movie_credits: Option[PersonMovieCredits],
  tv_credits: Option[PersonTvCredits])
    extends TmdbQueryableEntity
    with HasTmdbId

@JsonCodec case class CastMember(
  character: Option[String],
  character_name: Option[String],
  credit_id: Option[String],
  gender: Option[Int],
  id: Int,
  name: Option[String],
  order: Option[Int],
  profile_path: Option[String],
  // Computed fields
  normalizedName: Option[Slug] = None)

@JsonCodec case class CrewMember(
  credit_id: Option[String],
  department: Option[String],
  gender: Option[Int],
  id: Int,
  job: Option[String],
  name: Option[String],
  profile_path: Option[String])

@JsonCodec case class PersonMovieCredits(
  crew: List[Movie],
  cast: List[Movie])

@JsonCodec case class PersonTvCredits(
  crew: List[TvShow],
  cast: List[TvShow])

@JsonCodec
case class PersonCredits(
  crew: List[PersonCredit],
  cast: List[PersonCredit])

@JsonCodec
case class PersonCredit(
  adult: Option[Boolean],
  backdrop_path: Option[String],
  genre_ids: Option[List[Int]],
  department: Option[String],
  id: Int,
  character: Option[String],
  media_type: Option[MediaType],
  job: Option[String],
  original_language: Option[String],
  original_title: Option[String],
  overview: Option[String],
  popularity: Option[Double],
  poster_path: Option[String],
  release_date: Option[String],
  name: Option[String],
  title: Option[String],
  video: Option[Boolean],
  vote_average: Option[Double],
  vote_count: Option[Int])

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
  videos: Option[TvShowVideos])
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
  credits: Option[TvShowCredits])

@JsonCodec case class TvShowEpisode(
  air_date: Option[String],
//  crew: Option[List[Map[String, Any]]],
  episode_number: Option[Int],
//  guest_stars: Option[List[Map[String, Any]]],
  name: Option[String],
  overview: Option[String],
  id: Int,
  production_code: Option[String],
  season_number: Option[Int],
  still_path: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int])

@JsonCodec case class TvShowCredits(
  cast: Option[List[CastMember]],
  crew: Option[List[CrewMember]])

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

@JsonCodec case class ErrorResponse(
  status_message: Option[String],
  status_code: Option[Int],
  // We calculate this
  requested_item_id: Option[Int])

@JsonCodec case class Genre(
  id: Int,
  name: String)

@JsonCodec case class GenreListResponse(genres: List[Genre])

@JsonCodec case class ProductionCompany(
  name: Option[String],
  id: Int,
  logo_path: Option[String],
  origin_country: Option[String])

@JsonCodec case class ExternalIds(
  imdb_id: Option[String],
  freebase_mid: Option[String],
  freebase_id: Option[String],
  tvdb_id: Option[Int],
  tvrage_id: Option[Int],
  facebook_id: Option[String],
  instagram_id: Option[String],
  twitter_id: Option[String],
  id: Option[Int])

@JsonCodec case class Network(
  id: Int,
  headquarters: Option[String],
  homepage: Option[String],
  name: Option[String],
  origin_country: Option[String],
  images: Option[NetworkLogos])

@JsonCodec case class NetworkLogos(logos: Option[List[NetworkLogo]])

@JsonCodec case class NetworkLogo(
  id: Option[String],
  aspect_ratio: Option[Double],
  file_path: Option[String],
  height: Option[Int],
  file_type: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  width: Option[Int])

@JsonCodec case class Certification(
  certification: String,
  meaning: String,
  order: Int)

@JsonCodec case class CertificationListResponse(
  certifications: Map[String, List[Certification]])

@JsonCodec case class Collection(
  id: Int,
  name: String,
  overview: Option[String],
  backdrop_path: Option[String],
  parts: List[CollectionItem])

@JsonCodec case class CollectionItem(
  adult: Option[Boolean],
  backdrop_path: Option[String],
  genre_ids: Option[List[Int]],
  id: Int,
  original_language: Option[String],
  original_title: Option[String],
  overview: Option[String],
  release_date: Option[String],
  poster_path: Option[String],
  popularity: Option[Double],
  title: Option[String],
  video: Option[Boolean],
  vote_average: Option[Double],
  vote_count: Option[Double])

@JsonCodec case class PagedResultDates(
  minimum: String,
  maximum: String)

@JsonCodec case class PagedResult[T](
  page: Int,
  results: List[T],
  total_results: Int,
  total_pages: Int,
  dates: Option[PagedResultDates])

@JsonCodec case class TmdbError(
  status_code: Int,
  status_message: String,
  // We calculate this
  requested_item_id: Option[Int])
    extends Exception(status_message)
