package com.teletracker.common.elasticsearch

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.{
  EsItemCastMember,
  EsItemCrewMember,
  EsItemImage,
  EsItemVideo
}
import com.teletracker.common.util.Lists

object EsOrdering {
  implicit val forEsImageType: Ordering[EsImageType] =
    Ordering[String].on(_.toString)

  final val forEsImages: Ordering[EsItemImage] = Ordering
    .Tuple3[ExternalSource, EsImageType, String]
    .on(
      image =>
        (
          ExternalSource.fromString(image.provider_shortname),
          image.image_type,
          image.id
        )
    )

  final val forEsVideos: Ordering[EsItemVideo] = Ordering
    .Tuple4[ExternalSource, String, String, String]
    .on(
      video =>
        (
          ExternalSource.fromString(video.provider_shortname),
          video.provider_shortname,
          video.video_source,
          video.video_source_id
        )
    )

  final val forItemCastMember =
    (left: EsItemCastMember, right: EsItemCastMember) => {
      left.order <= right.order
    }

  final private val crewOrdering = Ordering
    .Tuple4[Option[Int], Option[String], Option[String], String](
      Lists.NullsLastOrdering[Int],
      Lists.NullsLastOrdering[String],
      Lists.NullsLastOrdering[String],
      Ordering[String]
    )

  val forItemCrewMember = (left: EsItemCrewMember, right: EsItemCrewMember) => {
    crewOrdering.lteq(
      (left.order, left.department, left.job, left.name),
      (right.order, right.department, right.job, right.name)
    )
  }
}
