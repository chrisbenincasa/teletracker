package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.tasks.general.BaseDiffTask
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.tasks.tmdb.export_tasks.MovieDumpFileRow
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FindDeletedMovies @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends BaseDiffTask[EsItemDumpRow, MovieDumpFileRow, String](
      sourceRetriever
    ) {

  override protected def extractRightData(
    right: MovieDumpFileRow
  ): Option[String] = {
//    if (right.adult) {
//      None
//    } else {
//    }
    Some(right.id.toString)
  }

  override protected def extractLeftData(
    left: EsItemDumpRow
  ): Option[String] = {
    if (left._source.adult.contains(false) && left._source.`type` == ItemType.Movie) {
      left._source.externalIdsGrouped.get(ExternalSource.TheMovieDb)
    } else {
      None
    }
  }
}
