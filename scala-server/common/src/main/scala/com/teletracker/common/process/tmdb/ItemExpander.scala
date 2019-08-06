package com.teletracker.common.process.tmdb

import com.teletracker.common.cache.TmdbLocalCache
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb._
import com.teletracker.common.util.json.circe._
import javax.inject.Inject
import shapeless.tag.@@
import scala.concurrent.{ExecutionContext, Future}

class ItemExpander @Inject()(
  tmdbClient: TmdbClient,
  cache: TmdbLocalCache
)(implicit executionContext: ExecutionContext) {
  def expandMovie(id: Int): Future[Movie] = expandMovie(id.toString)

  def expandMovie(id: String): Future[Movie] = {
    cache.getOrSetEntity(
      ThingType.Movie,
      id, {
        tmdbClient.makeRequest[Movie](
          s"movie/$id",
          Seq(
            "append_to_response" -> List(
              "release_dates",
              "credits",
              "external_ids"
            ).mkString(",")
          )
        )
      }
    )
  }

  def expandTvShow(id: String): Future[TvShow] = {
    cache.getOrSetEntity(ThingType.Show, id, {
      tmdbClient.makeRequest[TvShow](
        s"tv/$id",
        Seq(
          "append_to_response" -> List(
            "release_dates",
            "credits",
            "external_ids"
          ).mkString(",")
        )
      )
    })
  }

  def expandPerson(id: String): Future[Person] = {
    tmdbClient.makeRequest[Person](
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

  object ExpandItem extends shapeless.Poly1 {
    implicit val atMovieId: Case.Aux[String @@ MovieId, Future[Movie]] = at {
      m =>
        expandMovie(m)
    }

    implicit val atMovie: Case.Aux[Movie, Future[Movie]] = at { m =>
      expandMovie(m.id.toString)
    }

    implicit val atShow: Case.Aux[TvShow, Future[TvShow]] = at { s =>
      expandTvShow(s.id.toString)
    }

    implicit val atShowId: Case.Aux[String @@ TvShowId, Future[TvShow]] = at {
      s =>
        expandTvShow(s)
    }

    implicit val atPerson: Case.Aux[Person, Future[Person]] = at { p =>
      expandPerson(p.id.toString)
    }

    implicit val atPersonId: Case.Aux[String @@ PersonId, Future[Person]] = at {
      p =>
        expandPerson(p)
    }
  }
}
