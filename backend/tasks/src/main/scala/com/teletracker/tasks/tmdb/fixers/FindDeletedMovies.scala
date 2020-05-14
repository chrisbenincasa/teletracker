package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.tasks.general.BaseDiffTask
import com.teletracker.tasks.model.{EsItemDumpRow, MovieDumpFileRow}
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FindDeletedMovies @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends BaseDiffTask[MovieDumpFileRow, EsItemDumpRow, String](
      sourceRetriever
    ) {

  override protected def extractLeftData(
    data: MovieDumpFileRow
  ): Option[String] = {
//    if (data.adult) {
//      None
//    } else {
//    }
    Some(data.id.toString)
  }

  override protected def extractRightData(
    data: EsItemDumpRow
  ): Option[String] = {
    if (data._source.adult.contains(false) && data._source.`type` == ItemType.Movie) {
      data._source.externalIdsGrouped.get(ExternalSource.TheMovieDb)
    } else {
      None
    }
  }
}
