package com.teletracker.service.api.model

import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db.model.GenreType
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class MetadataResponse(
  genres: List[Genre],
  networks: List[Network])

object Network {
  implicit val codec: Codec[Network] = deriveCodec

  def fromStoredNetwork(storedNetwork: StoredNetwork) = {
    Network(
      id = storedNetwork.id,
      name = storedNetwork.name,
      slug = storedNetwork.slug,
      shortname = storedNetwork.shortname,
      homepage = storedNetwork.homepage,
      origin = storedNetwork.origin
    )
  }
}

case class Network(
  id: Int,
  name: String,
  slug: Slug,
  shortname: String,
  homepage: Option[String],
  origin: Option[String])

object Genre {
  implicit val codec: Codec[Network] = deriveCodec

  def fromStoredGenre(storedGenre: StoredGenre) = {
    Genre(
      id = storedGenre.id,
      name = storedGenre.name,
      slug = storedGenre.slug,
      `type` = storedGenre.genreTypes.toList
    )
  }
}

case class Genre(
  id: Int,
  name: String,
  slug: Slug,
  `type`: List[GenreType])
