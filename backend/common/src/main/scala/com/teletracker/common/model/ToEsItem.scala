package com.teletracker.common.model

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{
  EsExternalId,
  EsImageType,
  EsItem,
  EsItemImage,
  EsItemRating,
  EsItemVideo,
  EsOrdering
}
import com.teletracker.common.model.tmdb.{Movie, Person, TvShow}
import scala.util.Try

trait ToEsItem[T] {
  def esItemRating(t: T): Option[EsItemRating]

  def esItemImages(t: T): List[EsItemImage]

  def esExternalId(t: T): Option[EsExternalId]

  def esItemVideos(t: T): List[EsItemVideo]
}

object ToEsItem {
  implicit val forTmdbMovie: ToEsItem[Movie] = new ToEsItem[Movie] {
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
          EsItemImage(
            provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
            provider_shortname = ExternalSource.TheMovieDb.getName,
            id = backdrop,
            image_type = EsImageType.Backdrop
          )
        }),
        t.poster_path.map(poster => {
          EsItemImage(
            provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
            provider_shortname = ExternalSource.TheMovieDb.getName,
            id = poster,
            image_type = EsImageType.Poster
          )
        })
      ).flatten.sorted(EsOrdering.forEsImages)
    }

    override def esExternalId(t: Movie): Option[EsExternalId] = {
      Some(EsExternalId(ExternalSource.TheMovieDb, t.id.toString))
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

  implicit val forTmdbShow: ToEsItem[TvShow] = new ToEsItem[TvShow] {
    override def esItemRating(t: TvShow): Option[EsItemRating] = {
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

    override def esItemImages(t: TvShow): List[EsItemImage] = {
      List(
        t.backdrop_path.map(backdrop => {
          EsItemImage(
            provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
            provider_shortname = ExternalSource.TheMovieDb.getName,
            id = backdrop,
            image_type = EsImageType.Backdrop
          )
        }),
        t.poster_path.map(poster => {
          EsItemImage(
            provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
            provider_shortname = ExternalSource.TheMovieDb.getName,
            id = poster,
            image_type = EsImageType.Poster
          )
        })
      ).flatten.sorted(EsOrdering.forEsImages)
    }

    override def esExternalId(t: TvShow): Option[EsExternalId] = {
      Some(EsExternalId(ExternalSource.TheMovieDb, t.id.toString))
    }

    override def esItemVideos(t: TvShow): List[EsItemVideo] = {
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

  implicit val forTmdbPerson: ToEsItem[Person] = new ToEsItem[Person] {
    override def esItemRating(t: Person): Option[EsItemRating] = None

    override def esItemImages(t: Person): List[EsItemImage] =
      List(
        t.profile_path.map(profile => {
          EsItemImage(
            provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
            provider_shortname = ExternalSource.TheMovieDb.getName,
            id = profile,
            image_type = EsImageType.Profile
          )
        })
      ).flatten

    override def esExternalId(t: Person): Option[EsExternalId] =
      Some(EsExternalId(ExternalSource.TheMovieDb, t.id.toString))

    override def esItemVideos(t: Person): List[EsItemVideo] = Nil
  }
}
