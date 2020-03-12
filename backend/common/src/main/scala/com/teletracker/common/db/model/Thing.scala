package com.teletracker.common.db.model

import com.teletracker.common.db.UserThingDetails
import com.teletracker.common.model.tmdb._
import com.teletracker.common.util.Slug
import java.time.OffsetDateTime
import io.circe._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.syntax._
import shapeless.Witness
import java.util.UUID

case class Thing(
  id: UUID,
  name: String,
  normalizedName: Slug,
  `type`: ThingType,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  metadata: Option[ObjectMetadata],
  tmdbId: Option[String],
  popularity: Option[Double],
  genres: Option[List[Int]]) {
  def toPartial: PartialThing = {
    PartialThing(
      id,
      Some(name),
      Some(normalizedName),
      Some(`type`),
      Some(createdAt),
      Some(lastUpdatedAt),
      popularity,
      metadata.map(_.asJson),
      genreIds = genres.map(_.toSet)
    )
  }
}

object ThingRawFactory {
  def forObjectMetadata(
    id: UUID,
    name: String,
    normalizedName: Slug,
    `type`: ThingType,
    createdAt: OffsetDateTime,
    lastUpdatedAt: OffsetDateTime,
    metadata: Option[ObjectMetadata],
    tmdbId: Option[String],
    popularity: Option[Double],
    genres: Option[List[Int]]
  ): ThingRaw = {
    ThingRaw(
      id = id,
      name = name,
      normalizedName = normalizedName,
      `type` = `type`,
      createdAt = createdAt,
      lastUpdatedAt = lastUpdatedAt,
      metadata = metadata.map(_.asJson),
      tmdbId = tmdbId,
      popularity = popularity,
      genres = genres
    )
  }
}

case class ThingCastMember(
  id: UUID,
  slug: Slug,
  name: String,
  characterName: Option[String],
  relation: Option[PersonAssociationType],
  tmdbId: Option[String],
  popularity: Option[Double],
  order: Option[Int] = None,
  profilePath: Option[String] = None) {
  def withOrder(order: Option[Int]): ThingCastMember = {
    this.copy(order = order)
  }
  def withProfilePath(path: Option[String]) = this.copy(profilePath = path)
}

case class PartialThing(
  id: UUID,
  name: Option[String] = None,
  normalizedName: Option[Slug] = None,
  `type`: Option[ThingType] = None,
  createdAt: Option[OffsetDateTime] = None,
  lastUpdatedAt: Option[OffsetDateTime] = None,
  popularity: Option[Double] = None,
  metadata: Option[Json] = None,
  networks: Option[List[Network]] = None,
  seasons: Option[List[TvShowSeasonWithEpisodes]] = None,
  availability: Option[List[AvailabilityWithDetails]] = None,
  userMetadata: Option[UserThingDetails] = None,
  collections: Option[List[Collection]] = None,
  cast: Option[List[ThingCastMember]] = None,
  recommendations: Option[List[PartialThing]] = None,
  genreIds: Option[Set[Int]] = None) {
  def withAvailability(av: List[AvailabilityWithDetails]): PartialThing =
    this.copy(availability = Some(av))
  def withUserMetadata(userMeta: UserThingDetails): PartialThing =
    this.copy(userMetadata = Some(userMeta))
  def withCollections(collections: List[Collection]): PartialThing =
    this.copy(collections = Some(collections))
  def withCast(cast: List[ThingCastMember]): PartialThing =
    this.copy(cast = Some(cast))
  def withRecommendations(recommendations: List[PartialThing]) =
    this.copy(recommendations = Some(recommendations))
  def withRawMetadata(metadata: Json): PartialThing = {
    this.copy(metadata = Some(metadata))
  }
  def withGenres(genreIds: Set[Int]) = this.copy(genreIds = Some(genreIds))
  def clearMetadata: PartialThing = this.copy(metadata = None)
}

object ObjectMetadata {
  import shapeless.union._
  import shapeless._
  import shapeless.syntax.singleton._

  type TmdbExternalEntity =
    Union.`'movie -> Movie, 'show -> TvShow`.T

  def withTmdbMovie(movie: Movie): ObjectMetadata =
    ObjectMetadata(Some(Coproduct[TmdbExternalEntity]('movie ->> movie)))
  def withTmdbShow(show: TvShow): ObjectMetadata =
    ObjectMetadata(Some(Coproduct[TmdbExternalEntity]('show ->> show)))
}

case class ObjectMetadata(
  themoviedb: Option[ObjectMetadata.TmdbExternalEntity]) {
  import shapeless.union._

  val movieWitness = Witness('movie)
  val showWitness = Witness('show)

  def tmdbMovie: Option[Movie] = themoviedb.flatMap(_.get(movieWitness))
  def tmdbShow: Option[TvShow] = themoviedb.flatMap(_.get(showWitness))
}