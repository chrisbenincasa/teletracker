package com.teletracker.tasks.elasticsearch.fixers

import cats.implicits._
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.EsItemAlternativeTitle
import com.teletracker.common.model.tmdb.Movie
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class BackfillMovieAltTitles @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends CreateBackfillUpdateFile[Movie](teletrackerConfig) {
  private val countries = Set("US", "GB")

  override protected def shouldKeepItem(item: Movie): Boolean = {
    item.alternative_titles
      .exists(
        _.titles.exists(t => countries.contains(t.iso_3166_1))
      )
  }

  override protected def makeBackfillRow(item: Movie): TmdbBackfillOutputRow = {
    val titles = item.alternative_titles
      .map(_.titles)
      .nested
      .filter(t => countries.contains(t.iso_3166_1))
      .value
      .getOrElse(Nil)

    val altTitles = titles
      .map(
        alt =>
          EsItemAlternativeTitle(
            country_code = alt.iso_3166_1,
            title = alt.title,
            `type` = alt.`type`
          )
      )
      .asJson

    TmdbBackfillOutputRow(
      item.id,
      Map("alternative_titles" -> altTitles).asJson
    )
  }

  override protected def uniqueId(item: Movie): String = item.id.toString
}
