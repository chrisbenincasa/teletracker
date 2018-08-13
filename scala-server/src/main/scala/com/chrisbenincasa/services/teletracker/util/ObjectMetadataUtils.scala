package com.chrisbenincasa.services.teletracker.util

import com.chrisbenincasa.services.teletracker.db.model.{ExternalSource, ObjectMetadata, ThingType}
import com.chrisbenincasa.services.teletracker.model.tmdb._
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity.EntityIds
import shapeless.{Coproduct, Inl, Inr, tag}
import shapeless.union._

object ObjectMetadataUtils {
  def metadataMatchesId(objectMetadata: ObjectMetadata, entityId: EntityIds): Boolean = {
    entityId match {
      case Inl(movieId) => objectMetadata.themoviedb.flatMap(_.union.selectDynamic("movie")).exists((m: Movie) => m.id.toString == movieId)
      case Inr(Inl(showId)) => objectMetadata.themoviedb.flatMap(_.union.selectDynamic("show")).exists((m: TvShow) => m.id.toString == showId)
      case Inr(Inr(Inl(personId))) => objectMetadata.themoviedb.flatMap(_.union.selectDynamic("person")).exists((m: Person) => m.id.toString == personId)
      case _ => ???
    }
  }

  def metadataMatchesId(objectMetadata: ObjectMetadata, source: ExternalSource, typ: ThingType, id: String): Boolean = {
    source match {
      case ExternalSource.TheMovieDb =>
        val entityId: EntityIds = typ match {
          case ThingType.Movie => Coproduct[EntityIds](tag[MovieId][String](id))
          case ThingType.Show => Coproduct[EntityIds](tag[TvShowId][String](id))
          case ThingType.Person => Coproduct[EntityIds](tag[PersonId][String](id))
        }

        metadataMatchesId(objectMetadata, entityId)
      case _ => ???
    }
  }
}
