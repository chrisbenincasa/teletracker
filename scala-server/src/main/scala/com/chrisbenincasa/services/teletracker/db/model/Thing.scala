package com.chrisbenincasa.services.teletracker.db.model

import com.chrisbenincasa.services.teletracker.db.{CustomPostgresProfile, UserThingDetails}
import com.chrisbenincasa.services.teletracker.inject.DbImplicits
import com.chrisbenincasa.services.teletracker.model.tmdb._
import javax.inject.Inject
import io.circe._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import org.joda.time.DateTime

case class Thing(
  id: Option[Int],
  name: String,
  normalizedName: String,
  `type`: ThingType,
  createdAt: DateTime,
  lastUpdatedAt: DateTime,
  metadata: Option[ObjectMetadata]
) {
  def asPartial: PartialThing = {
    PartialThing(id, Some(name), Some(normalizedName), Some(`type`), Some(createdAt), Some(lastUpdatedAt), metadata)
  }
}

case class ThingRaw(
  id: Option[Int],
  name: String,
  normalizedName: String,
  `type`: ThingType,
  createdAt: DateTime,
  lastUpdatedAt: DateTime,
  metadata: Option[Json]
) {
  def asPartial: PartialThing = {
    val typedMeta = metadata.flatMap(rawMeta => {
     rawMeta.as[ObjectMetadata] match {
       case Left(err) =>
         err.printStackTrace()
         println(err.getMessage())
         None
       case Right(value) =>
         Some(value)
     }
    })
    PartialThing(id, Some(name), Some(normalizedName), Some(`type`), Some(createdAt), Some(lastUpdatedAt), typedMeta)
  }
}

case class PartialThing(
  id: Option[Int] = None,
  name: Option[String] = None,
  normalizedName: Option[String] = None,
  `type`: Option[ThingType] = None,
  createdAt: Option[DateTime] = None,
  lastUpdatedAt: Option[DateTime] = None,
  metadata: Option[ObjectMetadata] = None,
  networks: Option[List[Network]] = None,
  seasons: Option[List[TvShowSeasonWithEpisodes]] = None,
  availability: Option[List[AvailabilityWithDetails]] = None,
  userMetadata: Option[UserThingDetails] = None
) {
  def withAvailability(av: List[AvailabilityWithDetails]) = this.copy(availability = Some(av))
  def withUserMetadata(userMeta: UserThingDetails) = this.copy(userMetadata = Some(userMeta))

  def withRawMetadata(metadata: Json): PartialThing = {
    val typedMeta = metadata.as[ObjectMetadata] match {
      case Left(err) =>
        err.printStackTrace()
        println(err.getMessage())
        None
      case Right(value) =>
        Some(value)
    }

    this.copy(metadata = typedMeta)
  }
}

object ObjectMetadata {
  import shapeless.union._
  import shapeless._
  import shapeless.syntax.singleton._

  type TmdbExternalEntity = Union.`'movie -> Movie, 'show -> TvShow, 'person -> Person`.T

  def withTmdbMovie(movie: Movie): ObjectMetadata = ObjectMetadata(Some(Coproduct[TmdbExternalEntity]('movie ->> movie)))
  def withTmdbShow(show: TvShow): ObjectMetadata = ObjectMetadata(Some(Coproduct[TmdbExternalEntity]('show ->> show)))
  def withTmdbPerson(person: Person): ObjectMetadata = ObjectMetadata(Some(Coproduct[TmdbExternalEntity]('person ->> person)))
}

case class ObjectMetadata(
  themoviedb: Option[ObjectMetadata.TmdbExternalEntity]
)

class Things @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits
) {
  import profile.api._
  import dbImplicits._

  object Implicits {
    implicit val metaToJson = MappedColumnType.base[ObjectMetadata, Json](_.asJson, _.as[ObjectMetadata].right.get)
  }

  import Implicits._

  class ThingsTable(tag: Tag) extends Table[Thing](tag, "things") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def normalizedName = column[String]("normalized_name")
    def `type` = column[ThingType]("type")
    def createdAt = column[DateTime]("created_at", O.SqlType("timestamp with time zone"))
    def lastUpdatedAt = column[DateTime]("last_updated_at", O.SqlType("timestamp with time zone"))
    def metadata = column[Option[ObjectMetadata]]("metadata", O.SqlType("jsonb"))

    def uniqueSlugType = index("unique_slug_type", (normalizedName, `type`))

    override def * =
      (
        id.?,
        name,
        normalizedName,
        `type`,
        createdAt,
        lastUpdatedAt,
        metadata
      ) <> (Thing.tupled, Thing.unapply)
  }

  class ThingsTableRaw(tag: Tag) extends Table[ThingRaw](tag, "things") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def normalizedName = column[String]("normalized_name")
    def `type` = column[ThingType]("type")
    def createdAt = column[DateTime]("created_at", O.SqlType("timestamp with time zone"))
    def lastUpdatedAt = column[DateTime]("last_updated_at", O.SqlType("timestamp with time zone"))
    def metadata = column[Option[Json]]("metadata", O.SqlType("jsonb"))

    def uniqueSlugType = index("unique_slug_type", (normalizedName, `type`))

    override def * =
      (
        id.?,
        name,
        normalizedName,
        `type`,
        createdAt,
        lastUpdatedAt,
        metadata
      ) <> (ThingRaw.tupled, ThingRaw.unapply)
  }

  val query = TableQuery[ThingsTable]
  val rawQuery = TableQuery[ThingsTableRaw]
}
