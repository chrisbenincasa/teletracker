package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{model, EsImageType}

object EsItemImages {
  def forSource(
    source: ExternalSource,
    imageType: EsImageType,
    id: String
  ): EsItemImage = {
    model.EsItemImage(
      provider_id = source.getValue,
      provider_shortname = source.getName,
      image_type = imageType,
      id = id
    )
  }
}
