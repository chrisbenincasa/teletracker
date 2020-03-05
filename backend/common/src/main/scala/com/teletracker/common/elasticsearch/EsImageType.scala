package com.teletracker.common.elasticsearch

import io.circe.{Codec, Encoder}
import scala.util.Try

object EsImageType {
  import io.circe.Decoder

  final val PosterType = "poster"
  final val BackdropType = "backdrop"
  final val ProfileType = "profile"

  def fromString(str: String): Option[EsImageType] = str.toLowerCase() match {
    case PosterType   => Some(Poster)
    case BackdropType => Some(Backdrop)
    case ProfileType  => Some(Profile)
    case _            => None
  }

  implicit val codec: Codec[EsImageType] = Codec.from(
    Decoder.decodeString.emapTry(s => Try(fromString(s).get)),
    Encoder.encodeString.contramap[EsImageType](_.toString)
  )

  case object Poster extends EsImageType {
    override def toString: String = EsImageType.PosterType
  }

  case object Backdrop extends EsImageType {
    override def toString: String = EsImageType.BackdropType
  }

  case object Profile extends EsImageType {
    override def toString: String = EsImageType.ProfileType
  }
}

sealed trait EsImageType
