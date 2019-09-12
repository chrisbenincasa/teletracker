package com.teletracker.common.process.tmdb

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model
import com.teletracker.common.db.model.{ExternalId, ThingLike}
import com.teletracker.common.model.justwatch.PopularItem
import com.teletracker.common.model.tmdb
import com.teletracker.common.model.tmdb.TmdbWatchable
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

abstract class TmdbImporter(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {

  protected def handleExternalIds(
    entity: Either[ThingLike, model.TvShowEpisode],
    externalIds: Option[tmdb.ExternalIds],
    tmdbId: Option[String]
  ): Future[Option[ExternalId]] = {
    if (externalIds.isDefined || tmdbId.isDefined) {
      val id = tmdbId.orElse(externalIds.map(_.id.toString)).get
      thingsDbAccess.findExternalIdsByTmdbId(id).flatMap {
        case None =>
          val baseEid = model.ExternalId(
            None,
            None,
            None,
            Some(id),
            externalIds.flatMap(_.imdb_id),
            None,
            OffsetDateTime.now()
          )

          val eid = entity match {
            case Left(t)  => baseEid.copy(thingId = Some(t.id))
            case Right(t) => baseEid.copy(tvEpisodeId = t.id)
          }

          thingsDbAccess.upsertExternalIds(eid).map(Some(_))

        case Some(x) => Future.successful(Some(x))
      }
    } else {
      Future.successful(None)
    }
  }

  protected def matchJustWatchItem[W](
    entity: W,
    popularItems: List[PopularItem]
  )(implicit tmdbWatchable: TmdbWatchable[W]
  ): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring
        .getOrElse(Nil)
        .exists(
          s =>
            s.provider_type == "tmdb:id" && s.value.toInt.toString == tmdbWatchable
              .id(entity)
              .toString
        )
      val nameMatch = item.title.exists(tmdbWatchable.title(entity).contains)
      val yearMatch = item.original_release_year.exists(year => {
        tmdbWatchable.releaseYear(entity).contains(year)
      })

      idMatch || (nameMatch && yearMatch)
    })
  }
}
