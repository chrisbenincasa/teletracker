package com.teletracker.service.db.model

import com.google.inject.Provider
import io.circe.generic.JsonCodec
import javax.inject.Inject
import slick.jdbc.JdbcProfile
import com.teletracker.service.util.json.circe._

case class TvShowEpisode(
  id: Option[Int],
  number: Int,
  thingId: Int,
  seasonId: Int,
  name: String,
  productionCode: Option[String]) {
  def withAvailability(
    availability: Availability
  ): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, Some(availability))
  }

  def withAvailability(
    availability: Option[Availability]
  ): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, availability)
  }
}

@JsonCodec case class TvShowEpisodeWithAvailability(
  id: Option[Int],
  number: Int,
  seasonId: Int,
  availability: Option[Availability])

class TvShowEpisodes @Inject()(
  val driver: JdbcProfile,
  val things: Provider[Things],
  val tvShowSeasons: Provider[TvShowSeasons]) {

  import driver.api._

  class TvShowEpisodesTable(tag: Tag)
      extends Table[TvShowEpisode](tag, "tv_show_episodes") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def number = column[Int]("number")
    def thingId = column[Int]("thing_id")
    def seasonId = column[Int]("season_id")
    def name = column[String]("name")
    def productionCode = column[Option[String]]("production_code")

    def season =
      query join tvShowSeasons.get().query on (_.seasonId === _.id) map (_._2)
    def show =
      foreignKey("tv_show_episodes_show_fk", thingId, things.get().query)(_.id)

    override def * =
      (
        id.?,
        number,
        thingId,
        seasonId,
        name,
        productionCode
      ) <> (TvShowEpisode.tupled, TvShowEpisode.unapply)
  }

  val query = TableQuery[TvShowEpisodesTable]
}
