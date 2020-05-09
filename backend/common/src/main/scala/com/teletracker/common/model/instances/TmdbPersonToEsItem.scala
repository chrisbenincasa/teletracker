package com.teletracker.common.model.instances

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{model, EsImageType}
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItemImage,
  EsItemRating,
  EsItemVideo
}
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.Person

object PersonToEsItem extends ToEsItem[Person] {
  override def esItemRating(t: Person): Option[EsItemRating] = None

  override def esItemImages(t: Person): List[EsItemImage] =
    List(
      t.profile_path.map(profile => {
        model.EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = profile,
          image_type = EsImageType.Profile
        )
      })
    ).flatten

  override def esExternalIds(t: Person): List[EsExternalId] = {
    List(EsExternalId(ExternalSource.TheMovieDb, t.id.toString))
  }

  override def esItemVideos(t: Person): List[EsItemVideo] = Nil
}
