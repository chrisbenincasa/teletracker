package com.teletracker.common.model.instances

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{model, EsImageType, EsOrdering}
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItemImage,
  EsItemRating,
  EsItemVideo
}
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.Movie

object TmdbMovieToEsItem extends ToEsItem[Movie] {
  override def esItemRating(t: Movie): Option[EsItemRating] = {
    t.vote_average.map(voteAverage => {
      EsItemRating(
        provider_id = ExternalSource.TheMovieDb.ordinal(),
        provider_shortname = ExternalSource.TheMovieDb.getName,
        vote_average = voteAverage,
        vote_count = t.vote_count,
        weighted_average = None,
        weighted_last_generated = None
      )
    })
  }

  override def esItemImages(t: Movie): List[EsItemImage] = {
    List(
      t.backdrop_path.map(backdrop => {
        model.EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = backdrop,
          image_type = EsImageType.Backdrop
        )
      }),
      t.poster_path.map(poster => {
        model.EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = poster,
          image_type = EsImageType.Poster
        )
      })
    ).flatten.sorted(EsOrdering.forEsImages)
  }

  override def esExternalIds(t: Movie): List[EsExternalId] = {
    List(
      Some(EsExternalId(ExternalSource.TheMovieDb, t.id.toString)),
      t.external_ids
        .flatMap(_.tvdb_id)
        .map(
          tvDbId => EsExternalId(ExternalSource.TvDb, tvDbId.toString)
        ),
      t.external_ids
        .flatMap(_.imdb_id)
        .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
    ).flatten.sortBy(_.toString)
  }

  override def esItemVideos(t: Movie): List[EsItemVideo] = {
    t.videos
      .map(_.results)
      .getOrElse(Nil)
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
  }
}
