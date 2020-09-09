package com.teletracker.common.process.tmdb

import com.teletracker.common.cache.TmdbLocalCache
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object TmdbItemLookup {
  final val DefaultMovieAppendFields = List(
    "release_dates",
    "credits",
    "external_ids",
    "alternative_titles",
    "recommendations",
    "translations",
    "images",
    "videos"
  )

  final val DefaultTvShowAppendFields = List(
    "release_dates",
    "credits",
    "external_ids",
    "alternative_titles",
    "recommendations",
    "translations",
    "content_ratings",
    "images"
  )

  final val DefaultPersonAppendFields = List(
    "combined_credits",
    "images",
    "external_ids"
  )

  final val DefaultSeasonAppendFields = List(
    "external_ids",
    "credits",
    "images",
    "videos"
  )

  final val DefaultEpisodeAppendFields = List(
    "external_ids",
    "credits",
    "images",
    "videos"
  )
}

class TmdbItemLookup @Inject()(
  tmdbClient: TmdbClient,
  cache: TmdbLocalCache
)(implicit executionContext: ExecutionContext) {
  import TmdbItemLookup._

  def expandRaw(
    thingType: ItemType,
    id: Int,
    extraAppendFields: List[String] = Nil
  ): Future[Json] = {
    val path = thingType match {
      case ItemType.Movie  => "movie"
      case ItemType.Show   => "tv"
      case ItemType.Person => "person"
    }

    val defaultFields = thingType match {
      case ItemType.Movie  => DefaultMovieAppendFields
      case ItemType.Show   => DefaultTvShowAppendFields
      case ItemType.Person => DefaultPersonAppendFields
    }

    tmdbClient
      .makeRequest[Json](
        s"$path/$id",
        Seq(
          "append_to_response" -> (extraAppendFields ++ defaultFields).distinct
            .mkString(",")
        )
      )
      .map(json => {
        json.asObject
          .filter(_.contains("status_code"))
          .map(obj => {
            obj.+:("requested_item_id" -> id.asJson).asJson
          })
          .getOrElse(json)
      })
  }

  def expandMovie(
    id: Int,
    extraExpandFields: List[String] = Nil
  ): Future[Movie] = {
    cache.getOrSetEntity(
      ItemType.Movie,
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
      ItemType.Show,
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

  def expandTvShowRaw(
    id: Int,
    extraExpandFields: List[String] = Nil
  ): Future[Json] = {
    tmdbClient.makeRequest[Json](
      s"tv/$id",
      Seq(
        "append_to_response" -> (extraExpandFields ++ DefaultTvShowAppendFields).distinct
          .mkString(",")
      )
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

  def expandTvSeasonRaw(
    showId: Int,
    seasonId: Int
  ): Future[Json] = {
    tmdbClient.makeRequest[Json](
      s"tv/$showId/season/$seasonId",
      Seq(
        "append_to_response" -> DefaultSeasonAppendFields.mkString(",")
      )
    )
  }

  def expandTvEpisodeRaw(
    showId: Int,
    seasonNumber: Int,
    episodeNumber: Int
  ): Future[Json] = {
    tmdbClient.makeRequest[Json](
      s"tv/$showId/season/$seasonNumber/episode/$episodeNumber",
      Seq("append_to_response" -> DefaultEpisodeAppendFields.mkString(","))
    )
  }
}
