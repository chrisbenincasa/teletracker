package com.teletracker.tasks.elasticsearch.fixers

import cats.implicits._
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.EsItemVideo
import com.teletracker.common.model.tmdb.{Movie, TvShow}
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class BackfillMovieVideos @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends CreateBackfillUpdateFile[Movie](teletrackerConfig) {
  override protected def shouldKeepItem(item: Movie): Boolean = {
    item.videos.exists(_.results.nonEmpty)
  }

  override protected def makeBackfillRow(item: Movie): TmdbBackfillOutputRow = {
    val videos = item.videos
      .map(_.results)
      .nested
      .map(video => {
        EsItemVideo(
          provider_id = ExternalSource.TheMovieDb.getValue,
          provider_shortname = ExternalSource.TheMovieDb.getName,
          provider_source_id = video.id,
          name = video.name,
          language_code = video.iso_639_1,
          country_code = video.iso_3166_1,
          video_source = video.site.toLowerCase,
          video_source_id = video.key,
          size = video.size,
          video_type = video.`type`
        )
      })
      .value

    TmdbBackfillOutputRow(
      item.id,
      Map("videos" -> videos.asJson).asJson
    )
  }

  override protected def uniqueId(item: Movie): String = item.id.toString
}

class BackfillShowVideos @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends CreateBackfillUpdateFile[TvShow](teletrackerConfig) {
  override protected def shouldKeepItem(item: TvShow): Boolean = {
    item.videos.exists(_.results.nonEmpty)
  }

  override protected def makeBackfillRow(
    item: TvShow
  ): TmdbBackfillOutputRow = {
    val videos = item.videos
      .map(_.results)
      .nested
      .map(video => {
        EsItemVideo(
          provider_id = ExternalSource.TheMovieDb.getValue,
          provider_shortname = ExternalSource.TheMovieDb.getName,
          provider_source_id = video.id,
          name = video.name,
          language_code = video.iso_639_1,
          country_code = video.iso_3166_1,
          video_source = video.site.toLowerCase,
          video_source_id = video.key,
          size = video.size,
          video_type = video.`type`
        )
      })
      .value

    TmdbBackfillOutputRow(
      item.id,
      Map("videos" -> videos.asJson).asJson
    )
  }

  override protected def uniqueId(item: TvShow): String = item.id.toString
}
