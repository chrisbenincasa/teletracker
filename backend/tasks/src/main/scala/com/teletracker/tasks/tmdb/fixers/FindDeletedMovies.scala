package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.tasks.general.BaseDiffTask
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.tasks.tmdb.export_tasks.MovieDumpFileRow
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject

class FindDeletedMovies @Inject()(sourceRetriever: SourceRetriever)
    extends BaseDiffTask[MovieDumpFileRow, EsItemDumpRow, String](
      sourceRetriever
    ) {

  override protected def extractLeftData(
    left: MovieDumpFileRow
  ): Option[String] = {
    if (left.adult) {
      None
    } else {
      Some(left.id.toString)
    }
  }

  override protected def extractRightData(
    right: EsItemDumpRow
  ): Option[String] = {
//    if (right._source.adult.exists(identity)) {
//      None
//    } else {
//    }
    if (right._source.adult.isEmpty || right._source.adult.exists(identity)) {
      right._source.externalIdsGrouped.get(ExternalSource.TheMovieDb)
    } else {
      None
    }
  }
}
