package com.teletracker.common.db.access

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.db.DbMonitoring
import com.teletracker.common.db.model._
import com.teletracker.common.inject.{
  BaseDbProvider,
  DbImplicits,
  SyncDbProvider,
  SyncPath
}
import com.teletracker.common.util.Slug
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SyncGenresDbAccess @Inject()(
  @SyncPath override val provider: BaseDbProvider,
  override val genres: Genres,
  override val genreReferences: GenreReferences,
  override val things: Things,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends GenresDbAccess(
      provider,
      genres,
      genreReferences,
      things,
      dbImplicits,
      dbMonitoring
    )

class GenresDbAccess(
  val provider: BaseDbProvider,
  val genres: Genres,
  val genreReferences: GenreReferences,
  val things: Things,
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

  def findAllGenreReferences(
    genreType: ExternalSource
  ): Future[Seq[GenreReference]] = {
    run {
      genreReferences.query.filter(_.externalSource === genreType).result
    }
  }

  def findMostPopularThingsForGenre(
    genreId: Int,
    limit: Int = 25
  ) = {
    run {
      things.rawQuery
        .filter(_.genres @> List(genreId))
        .sortBy(_.popularity.desc.nullsLast)
        .take(25)
        .result
    }
  }

//  def saveGenreAssociation(thingGenre: ThingNetwork) = {
//    run {
//      thingNetworks.query.insertOrUpdate(thingNetwork)
//    }
//  }
}
