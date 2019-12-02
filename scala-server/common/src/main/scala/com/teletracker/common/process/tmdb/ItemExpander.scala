package com.teletracker.common.process.tmdb

import com.teletracker.common.cache.TmdbLocalCache
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import com.teletracker.common.util.json.circe._
import io.circe.Json
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object ItemExpander {
  final val DefaultMovieAppendFields = List(
    "release_dates",
    "credits",
    "external_ids",
    "alternative_titles"
  )

  final val DefaultTvShowAppendFields = List(
    "release_dates",
    "credits",
    "external_ids",
    "alternative_titles"
  )

  final val DefaultPersonAppendFields = List(
    "combined_credits",
    "images",
    "external_ids"
  )
}

class ItemExpander @Inject()(
  tmdbClient: TmdbClient,
  cache: TmdbLocalCache
)(implicit executionContext: ExecutionContext) {
  import ItemExpander._

  def expandRaw(
    thingType: ThingType,
    id: Int,
    extraAppendFields: List[String] = Nil
  ): Future[Json] = {
    val path = thingType match {
      case ThingType.Movie  => "movie"
      case ThingType.Show   => "tv"
      case ThingType.Person => "person"
    }

    val defaultFields = thingType match {
      case ThingType.Movie  => DefaultMovieAppendFields
      case ThingType.Show   => DefaultTvShowAppendFields
      case ThingType.Person => DefaultPersonAppendFields
    }

    tmdbClient.makeRequest[Json](
      s"$path/$id",
      Seq(
        "append_to_response" -> (extraAppendFields ++ defaultFields).distinct
          .mkString(",")
      )
    )
  }

  def expandMovie(
    id: Int,
    extraExpandFields: List[String] = Nil
  ): Future[Movie] = {
    cache.getOrSetEntity(
      ThingType.Movie,
      id, {
        tmdbClient.makeRequest[Movie](
          s"movie/$id",
          Seq(
            "append_to_response" -> (extraExpandFields ++ DefaultMovieAppendFields).distinct
              .mkString(",")
          )
        )
      }
    )
  }

  def expandMovieRaw(
    id: Int,
    extraExpandFields: List[String] = Nil
  ): Future[Json] = {
    tmdbClient.makeRequest[Json](
      s"movie/$id",
      Seq(
        "append_to_response" -> (extraExpandFields ++ DefaultMovieAppendFields).distinct
          .mkString(",")
      )
    )
  }

  def expandTvShow(
    id: Int,
    extraExpandFields: List[String] = Nil
  ): Future[TvShow] = {
    cache.getOrSetEntity(
      ThingType.Show,
      id, {
        tmdbClient.makeRequest[TvShow](
          s"tv/$id",
          Seq(
            "append_to_response" -> (extraExpandFields ++ List(
              "release_dates",
              "credits",
              "external_ids"
            )).distinct.mkString(",")
          )
        )
      }
    )
  }

  def expandPerson(id: Int): Future[TmdbPerson] = {
    tmdbClient.makeRequest[TmdbPerson](
      s"person/$id",
      Seq(
        "append_to_response" -> List(
          "combined_credits",
          "images",
          "external_ids"
        ).mkString(",")
      )
    )
  }

  def expandPersonRaw(id: Int): Future[Json] = {
    tmdbClient.makeRequest[Json](
      s"person/$id",
      Seq(
        "append_to_response" -> List(
          "combined_credits",
          "images",
          "external_ids"
        ).mkString(",")
      )
    )
  }
}
