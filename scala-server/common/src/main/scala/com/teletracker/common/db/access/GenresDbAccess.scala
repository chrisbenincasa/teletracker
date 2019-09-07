package com.teletracker.common.db.access

import com.teletracker.common.db.DbMonitoring
import com.teletracker.common.db.model._
import com.teletracker.common.inject.{DbImplicits, DbProvider}
import com.teletracker.common.util.Slug
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GenresDbAccess @Inject()(
  val provider: DbProvider,
  val genres: Genres,
  val genreReferences: GenreReferences,
//  val thingNetworks: ThingNetworks,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends DbAccess(dbMonitoring) {
  import dbImplicits._
  import provider.driver.api._

  def findGenresBySlugs(slugs: Set[Slug]) = {
    if (slugs.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        genres.query.filter(_.slug inSetBind slugs).result
      }
    }
  }

  def findAllGenres(): Future[Seq[(GenreReference, Genre)]] = {
    run {
      genreReferences.query
        .flatMap(ref => ref.genre.map(ref -> _))
        .result
    }
  }

  def saveGenre(genre: Genre) = {
    run {
      (genres.query returning genres.query.map(_.id) into (
        (
          n,
          id
        ) => n.copy(id = Some(id))
      )) += genre
    }
  }

  def saveGenreReference(networkReference: GenreReference) = {
    run {
      genreReferences.query += networkReference
    }
  }

//  def saveGenreAssociation(thingGenre: ThingNetwork) = {
//    run {
//      thingNetworks.query.insertOrUpdate(thingNetwork)
//    }
//  }
}
