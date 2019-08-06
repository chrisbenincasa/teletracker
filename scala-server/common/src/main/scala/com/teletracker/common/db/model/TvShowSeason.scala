package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.google.inject.Provider
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.time.LocalDate
import com.teletracker.common.util.json.circe._
import java.util.UUID

case class TvShowSeason(
  id: Option[Int],
  number: Int,
  showId: UUID,
  overview: Option[String],
  airDate: Option[LocalDate])

@JsonCodec case class TvShowSeasonWithEpisodes(
  id: Option[Int],
  number: Int,
  overview: Option[String],
  airDate: Option[LocalDate],
  episodes: Option[List[TvShowEpisodeWithAvailability]])

class TvShowSeasons @Inject()(
  val driver: CustomPostgresProfile,
  val things: Provider[Things]) {
  import driver.api._

  class TvShowSeasonsTable(tag: Tag)
      extends Table[TvShowSeason](tag, "tv_show_seasons") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def number = column[Int]("number")
    def showId = column[UUID]("show_id")
    def overview = column[Option[String]]("overview")
    def airDate = column[Option[LocalDate]]("air_date")

    def show =
      foreignKey("tv_show_seasons_show_id_fk", showId, things.get().query)(_.id)

    override def * =
      (
        id.?,
        number,
        showId,
        overview,
        airDate
      ) <> (TvShowSeason.tupled, TvShowSeason.unapply)
  }

  val query = TableQuery[TvShowSeasonsTable]
}
