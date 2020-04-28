package com.teletracker.common.model.tmdb

import io.circe.generic.JsonCodec
import io.circe._
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.teletracker.common.util.json.circe._

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
  videos: Option[MovieVideos],
  translations: Option[MovieTranslations],
  images: Option[MovieImages])
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

@JsonCodec case class MovieTranslations(
  translations: Option[List[MovieTranslation]])

@JsonCodec case class MovieTranslation(
  iso_3166_1: Option[String],
  iso_639_1: Option[String],
  name: Option[String],
  english_name: Option[String],
  data: Option[MovieTranslationData])

@JsonCodec case class MovieTranslationData(
  title: Option[String],
  overview: Option[String],
  homepage: Option[String])

@JsonCodec case class MovieImages(
  backdrops: Option[List[MovieImage]],
  posters: Option[List[MovieImage]])

@JsonCodec case class MovieImage(
  aspect_ratio: Option[Double],
  file_path: Option[String],
  height: Option[Int],
  iso_629_1: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  width: Option[Int])
