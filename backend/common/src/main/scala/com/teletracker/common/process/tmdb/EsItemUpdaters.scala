package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{EsItemImage, EsOrdering}

object EsItemUpdaters {

  /**
    * Given a new set of images from the same source, replace all existing images from that source, preserving
    * images from other sources
    */
  def updateImages(
    newImageSource: ExternalSource,
    newImages: List[EsItemImage],
    existingImages: List[EsItemImage]
  ): List[EsItemImage] = {
    require(
      newImages
        .map(_.provider_shortname)
        .map(ExternalSource.fromString)
        .forall(_ == newImageSource)
    )

    (existingImages.filterNot(
      image =>
        ExternalSource.fromString(image.provider_shortname) == newImageSource
    ) ++ newImages).sorted(EsOrdering.forEsImages)
  }
}
