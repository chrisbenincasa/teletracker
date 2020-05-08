package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ExternalSource
import io.circe.{Codec, Decoder, Encoder}

case class EsExternalId(
  provider: String,
  id: String) {
  override def toString: String = {
    s"${provider}${EsExternalId.SEPARATOR}$id"
  }
}

object EsExternalId {
  final private val SEPARATOR = "__"

  implicit val esExternalIdCodec: Codec[EsExternalId] = Codec.from(
    Decoder.decodeString.map(EsExternalId.parse),
    Encoder.encodeString.contramap[EsExternalId](_.toString)
  )

  def apply(
    externalSources: ExternalSource,
    id: String
  ): EsExternalId = new EsExternalId(externalSources.getName, id)

  def parse(value: String): EsExternalId = {
    val Array(provider, id) = value.split(SEPARATOR, 2)
    EsExternalId(provider, id)
  }

  def fromMap(map: Map[ExternalSource, String]): Option[List[EsExternalId]] =
    if (map.isEmpty) None
    else Some(map.toList.map(Function.tupled(apply)))
}
