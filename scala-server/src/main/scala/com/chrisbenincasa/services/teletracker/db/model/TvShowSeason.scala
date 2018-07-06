package com.chrisbenincasa.services.teletracker.db.model

import com.google.inject.Provider
import io.circe.generic.JsonCodec
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class TvShowSeason(
  id: Option[Int],
  number: Int,
  showId: Int
)

@JsonCodec case class TvShowSeasonWithEpisodes(
  id: Option[Int],
  number: Int,
  episodes: Option[List[TvShowEpisodeWithAvailability]]
)

class TvShowSeasons @Inject()(
  val driver: JdbcProfile,
  val episodes: Provider[TvShowEpisodes]
) {
  import driver.api._

  class TvShowSeasonsTable(tag: Tag) extends Table[TvShowSeason](tag, "tv_show_seasons") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def number = column[Int]("number")
    def showId = column[Int]("show_id")

    def show = foreignKey("tv_show_seasons_show_id_fk", showId, episodes.get().query)(_.id)

    override def * =
      (
        id.?,
        number,
        showId
      ) <> (TvShowSeason.tupled, TvShowSeason.unapply)
  }

  val query = TableQuery[TvShowSeasonsTable]
}
