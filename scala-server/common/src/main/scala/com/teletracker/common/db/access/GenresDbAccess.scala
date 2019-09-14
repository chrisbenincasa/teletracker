package com.teletracker.common.db.access

import com.teletracker.common.db.model._
import com.teletracker.common.db.util.InhibitFilter
import com.teletracker.common.db.{BaseDbProvider, DbImplicits, DbMonitoring}
import com.teletracker.common.util.Slug
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class GenresDbAccess @Inject()(
  val provider: BaseDbProvider,
  val genres: Genres,
  val thingGenres: ThingGenres,
  val genreReferences: GenreReferences,
  val things: Things,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {
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

  def findGenresForThing(thingId: UUID) = {
    run {
      thingGenres.query.filter(_.thingId === thingId).result
    }
  }

  def findMostPopularThingsForGenre(
    genreId: Int,
    thingType: Option[ThingType],
    limit: Int = 25
  ): Future[Seq[ThingRaw]] = {
    run {
      val query = for {
        tg <- thingGenres.query
        if tg.genreId === genreId
        t <- things.rawQuery
        if t.id === tg.thingId
      } yield {
        t
      }

      InhibitFilter(query)
        .filter(thingType)(t => _.`type` === t)
        .query
        .sortBy(_.popularity.desc.nullsLast)
        .take(25)
        .result
    }
  }
}
