package com.teletracker.common.model.tmdb

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.teletracker.common.util.json.circe._
import shapeless.{:+:, CNil}

trait TmdbQueryableEntity

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

@JsonCodec case class Image(
  aspect_ratio: Option[Double],
  file_path: Option[String],
  height: Option[Int],
  iso_639_1: Option[String],
  vote_average: Option[Double],
  vote_count: Option[Int],
  width: Option[Int])

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
