package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.EsOrdering
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItem,
  EsItemImage,
  EsItemVideo
}
import com.teletracker.common.model.ToEsItem

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

  def updateVideos(
    newVideoSource: ExternalSource,
    newVideos: List[EsItemVideo],
    existingVideos: List[EsItemVideo]
  ): List[EsItemVideo] = {
    require(
      newVideos
        .map(_.provider_shortname)
        .map(ExternalSource.fromString)
        .forall(_ == newVideoSource)
    )

    (existingVideos.filterNot(
      image =>
        ExternalSource.fromString(image.provider_shortname) == newVideoSource
    ) ++ newVideos).sorted(EsOrdering.forEsVideos)
  }

  def updateExternalIds[T](
    externalItem: T,
    existingItem: EsItem
  )(implicit toEsItem: ToEsItem[T]
  ): List[EsExternalId] = {
    toEsItem
      .esExternalIds(externalItem)
      .foldLeft(existingItem.externalIdsGrouped)(
        (acc, id) => acc.updated(ExternalSource.fromString(id.provider), id.id)
      )
      .toList
      .map(Function.tupled(EsExternalId.apply))
  }
}
