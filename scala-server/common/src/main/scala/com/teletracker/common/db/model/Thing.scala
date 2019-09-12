package com.teletracker.common.db.model

import com.teletracker.common.db.{CustomPostgresProfile, DbImplicits}
import com.teletracker.common.db.access.UserThingDetails
import com.teletracker.common.model.tmdb._
import com.teletracker.common.util.Slug
import java.time.OffsetDateTime
import javax.inject.Inject
import io.circe._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.syntax._
import shapeless.Witness
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.lifted.MappedProjection
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
  popularity: Option[Double]) {
  def toPartial: PartialThing = {
    PartialThing(
      id,
      Some(name),
      Some(normalizedName),
      Some(`type`),
      Some(createdAt),
      Some(lastUpdatedAt),
      metadata.map(_.asJson)
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

  def tmdbMovie: Option[Movie] = themoviedb.flatMap(_.get(movieWitness))
}

class Things @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits) {
  import profile.api._
  import dbImplicits._

  object Implicits {
    implicit val metaToJson
      : JdbcType[ObjectMetadata] with BaseTypedType[ObjectMetadata] =
      MappedColumnType
        .base[ObjectMetadata, Json](_.asJson, _.as[ObjectMetadata].right.get)
  }

  import Implicits._

  class ThingsTable(tag: Tag) extends Table[Thing](tag, "things") {
    def id = column[UUID]("id", O.PrimaryKey)
    def name = column[String]("name")
    def normalizedName = column[Slug]("normalized_name")
    def `type` = column[ThingType]("type")
    def createdAt =
      column[OffsetDateTime](
        "created_at",
        O.SqlType("timestamp with time zone")
      )
    def lastUpdatedAt =
      column[OffsetDateTime](
        "last_updated_at",
        O.SqlType("timestamp with time zone")
      )
    def metadata =
      column[Option[ObjectMetadata]]("metadata", O.SqlType("jsonb"))
    def tmdbId = column[Option[String]]("tmdb_id")
    def popularity = column[Option[Double]]("popularity")

    def uniqueSlugType = index("unique_slug_type", (normalizedName, `type`))

    override def * =
      (
        id,
        name,
        normalizedName,
        `type`,
        createdAt,
        lastUpdatedAt,
        metadata,
        tmdbId,
        popularity
      ) <> (Thing.tupled, Thing.unapply)
  }

  class ThingsTableRaw(tag: Tag) extends Table[ThingRaw](tag, "things") {
    def id = column[UUID]("id", O.PrimaryKey)
    def name = column[String]("name")
    def normalizedName = column[Slug]("normalized_name")
    def `type` = column[ThingType]("type")
    def createdAt =
      column[OffsetDateTime](
        "created_at",
        O.SqlType("timestamp with time zone")
      )
    def lastUpdatedAt =
      column[OffsetDateTime](
        "last_updated_at",
        O.SqlType("timestamp with time zone")
      )
    def metadata = column[Option[Json]]("metadata", O.SqlType("jsonb"))
    def tmdbId = column[Option[String]]("tmdb_id")
    def popularity = column[Option[Double]]("popularity")
    def genres = column[Option[List[Int]]]("genres")

    def uniqueSlugType = index("unique_slug_type", (normalizedName, `type`))

    def allCols(includeMetadata: Boolean) = (
      id,
      name,
      normalizedName,
      `type`,
      createdAt,
      lastUpdatedAt,
      if (includeMetadata) metadata else Rep.None[Json],
      tmdbId,
      popularity,
      genres
    )

    def projWithMetadata(includeMetadata: Boolean): MappedProjection[
      ThingRaw,
      (
        UUID,
        String,
        Slug,
        ThingType,
        OffsetDateTime,
        OffsetDateTime,
        Option[Json],
        Option[String],
        Option[Double],
        Option[List[Int]]
      )
    ] =
      (
        id,
        name,
        normalizedName,
        `type`,
        createdAt,
        lastUpdatedAt,
        if (includeMetadata) metadata else Rep.None[Json],
        tmdbId,
        popularity,
        genres
      ) <> (ThingRaw.tupled, ThingRaw.unapply)

    override def * =
      projWithMetadata(true)
  }

  val query = TableQuery[ThingsTable]
  val rawQuery = TableQuery[ThingsTableRaw]
}
