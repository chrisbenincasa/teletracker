package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TvShowSeasonCountDump @Inject()(
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[PartialEsDumpRow, Int] {
  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander.expandTvShowRaw(currentId).map(_.noSpaces)
  }

  override protected def getCurrentId(item: PartialEsDumpRow): Int = {
    item.external_ids.collectFirst {
      case EsExternalId(typ, id) if typ == ExternalSource.TheMovieDb.getName =>
        id.toInt
    }.get
  }

  override protected def baseFileName: String = "seasons"
}

@JsonCodec
case class PartialEsDumpRow(
  `type`: ItemType,
  external_ids: List[EsExternalId],
  id: UUID)
