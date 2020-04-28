package com.teletracker.common.model.tmdb

import com.teletracker.common.util.Slug
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.shapes._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.teletracker.common.util.json.circe._
import shapeless.{:+:, CNil}

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
