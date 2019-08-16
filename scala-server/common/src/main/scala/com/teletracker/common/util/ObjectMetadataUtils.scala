package com.teletracker.common.util

import com.teletracker.common.db.model.{
  ExternalSource,
  ObjectMetadata,
  ThingType
}
import com.teletracker.common.external.Ids.ExternalIds
import com.teletracker.common.external.{MovieId, PersonId, TvShowId}
import com.teletracker.common.model.tmdb.{Movie, Person, TvShow}
import shapeless.{tag, Coproduct, Inl, Inr}
import shapeless.union._

object ObjectMetadataUtils {
  def metadataMatchesId(
    objectMetadata: ObjectMetadata,
    entityId: ExternalIds
  ): Boolean = {
    entityId match {
      case Inl(movieId) =>
        objectMetadata.themoviedb
          .flatMap(_.union.selectDynamic("movie"))
          .exists((m: Movie) => m.id.toString == movieId)
      case Inr(Inl(showId)) =>
        objectMetadata.themoviedb
          .flatMap(_.union.selectDynamic("show"))
          .exists((m: TvShow) => m.id.toString == showId)
      case Inr(Inr(Inl(personId))) =>
        objectMetadata.themoviedb
          .flatMap(_.union.selectDynamic("person"))
          .exists((m: Person) => m.id.toString == personId)
      case _ => ???
    }
  }

  def metadataMatchesId(
    objectMetadata: ObjectMetadata,
    source: ExternalSource,
    typ: ThingType,
    id: String
  ): Boolean = {
    source match {
      case ExternalSource.TheMovieDb =>
        val entityId: ExternalIds = typ match {
          case ThingType.Movie =>
            Coproduct[ExternalIds](tag[MovieId][String](id))
          case ThingType.Show =>
            Coproduct[ExternalIds](tag[TvShowId][String](id))
          case ThingType.Person =>
            Coproduct[ExternalIds](tag[PersonId][String](id))
        }

        metadataMatchesId(objectMetadata, entityId)
      case _ => ???
    }
  }
}
