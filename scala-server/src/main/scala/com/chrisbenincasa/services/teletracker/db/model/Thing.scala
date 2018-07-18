package com.chrisbenincasa.services.teletracker.db.model

import com.chrisbenincasa.services.teletracker.db.CustomPostgresProfile
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
)

case class ThingWithDetails(
  id: Int,
  name: String,
  normalizedName: String,
  `type`: ThingType,
  createdAt: DateTime,
  lastUpdatedAt: DateTime,
  networks: Option[List[Network]],
  seasons: Option[List[TvShowSeasonWithEpisodes]],
  metadata: Option[ObjectMetadata]
)

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

    def uniqueSlugType = index("unique_slug_type", (normalizedName, `type`), true)

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

  val query = TableQuery[ThingsTable]
}
