package com.teletracker.common.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile
import java.util.UUID

case class ExternalId(
  id: Option[Int],
  thingId: Option[UUID],
  tvEpisodeId: Option[Int],
  tmdbId: Option[String],
  imdbId: Option[String],
  netflixId: Option[String],
  lastUpdatedAt: java.sql.Timestamp)

class ExternalIds @Inject()(
  val driver: JdbcProfile,
  val things: Things,
  val tvShowEpisodes: TvShowEpisodes) {
  import driver.api._

  class ExternalIdsTable(tag: Tag)
      extends Table[ExternalId](tag, "external_ids") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def thingId = column[Option[UUID]]("thing_id")
    def tvEpisodeId = column[Option[Int]]("tv_episode_id")
    def tmdbId = column[Option[String]]("tmdb_id")
    def imdbId = column[Option[String]]("imdb_id")
    def netflixId = column[Option[String]]("netflix_id")
    def lastUpdatedAt =
      column[java.sql.Timestamp](
        "last_updated_at",
        O.SqlType("timestamp with time zone default now()")
      )

    def thing =
      foreignKey("external_ids_thing_fk", thingId, things.query)(_.id.?)
    def thingRaw =
      foreignKey("external_ids_thing_raw_fk", thingId, things.rawQuery)(_.id.?)
    def episode =
      foreignKey("external_ids_episodes_fk", tvEpisodeId, tvShowEpisodes.query)(
        _.id.?
      )

    override def * =
      (
        id.?,
        thingId,
        tvEpisodeId,
        tmdbId,
        imdbId,
        netflixId,
        lastUpdatedAt
      ) <> (ExternalId.tupled, ExternalId.unapply)
  }

  val query = TableQuery[ExternalIdsTable]
}
