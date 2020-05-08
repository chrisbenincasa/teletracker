package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.EsImageType
import com.teletracker.common.elasticsearch.model.EsItemImages
import org.scalatest.FlatSpec

class EsItemUpdatersTest extends FlatSpec {
  "EsItemUpdaters" should "add, remove, and update images for an ExternalSource" in {
    val existingImages = List(
      EsItemImages
        .forSource(ExternalSource.TheMovieDb, EsImageType.Poster, "id1"),
      EsItemImages
        .forSource(ExternalSource.TheMovieDb, EsImageType.Poster, "id2"),
      EsItemImages
        .forSource(ExternalSource.TvDb, EsImageType.Backdrop, "id1")
    )

    val newImages = List(
      EsItemImages
        .forSource(ExternalSource.TheMovieDb, EsImageType.Backdrop, "id1"),
      EsItemImages
        .forSource(ExternalSource.TheMovieDb, EsImageType.Poster, "id2")
    )

    val alteredImages = EsItemUpdaters.updateImages(
      ExternalSource.TheMovieDb,
      newImages,
      existingImages
    )

    assertResult(
      alteredImages
    )(
      newImages ++ List(
        EsItemImages
          .forSource(ExternalSource.TvDb, EsImageType.Backdrop, "id1")
      )
    )
  }
}
