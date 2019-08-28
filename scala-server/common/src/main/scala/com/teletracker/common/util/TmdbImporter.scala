package com.teletracker.common.util

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model
import com.teletracker.common.db.model.{ExternalId, ThingLike}
import com.teletracker.common.model.tmdb
import java.sql.Timestamp
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
            new Timestamp(System.currentTimeMillis())
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
}
