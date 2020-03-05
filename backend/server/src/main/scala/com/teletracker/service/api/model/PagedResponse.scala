package com.teletracker.service.api.model

import com.teletracker.common.model.Paging
import io.circe.Codec

object PagedResponse {
  implicit def codec[T](implicit tCodec: Codec[T]): Codec[PagedResponse[T]] =
    io.circe.generic.semiauto.deriveCodec
}

case class PagedResponse[T](
  data: List[T],
  paging: Option[Paging])
