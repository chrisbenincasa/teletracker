package com.chrisbenincasa.services.teletracker.db.model

import com.google.inject.Provider
import io.circe.generic.JsonCodec
import javax.inject.Inject
import slick.jdbc.JdbcProfile
import com.chrisbenincasa.services.teletracker.util.json.circe._

case class TvShowEpisode(
  id: Option[Int],
  number: Int,
  seasonId: Int
) {
  def withAvailability(availability: Availability): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, Some(availability))
  }

  def withAvailability(availability: Option[Availability]): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, availability)
  }
}

@JsonCodec case class TvShowEpisodeWithAvailability(
  id: Option[Int],
  number: Int,
  seasonId: Int,
  availability: Option[Availability]
)

class TvShowEpisodes @Inject()(
  val driver: JdbcProfile,
  val tvShowSeasons: Provider[TvShowSeasons]
) {

  import driver.api._

  class TvShowEpisodesTable(tag: Tag) extends Table[TvShowEpisode](tag, "tv_show_episodes") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def number = column[Int]("number")
    def seasonId = column[Int]("season_id")

    def season = foreignKey("tv_show_episodes_show_season_fk", seasonId, tvShowSeasons.get().query)(_.id)

    override def * =
      (
        id.?,
        number,
        seasonId
      ) <> (TvShowEpisode.tupled, TvShowEpisode.unapply)
  }

  val query = TableQuery[TvShowEpisodesTable]
}
