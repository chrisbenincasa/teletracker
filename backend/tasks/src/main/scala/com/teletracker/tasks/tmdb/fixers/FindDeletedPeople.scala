package com.teletracker.tasks.tmdb.fixers

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.tasks.general.BaseDiffTask
import com.teletracker.tasks.model.{EsPersonDumpRow, PersonDumpFileRow}
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FindDeletedPeople @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends BaseDiffTask[PersonDumpFileRow, EsPersonDumpRow, String](
      sourceRetriever
    ) {

  override protected def extractLeftData(
    left: PersonDumpFileRow
  ): Option[String] = {
    if (left.adult) {
      None
    } else {
      Some(left.id.toString)
    }
  }

  override protected def extractRightData(
    right: EsPersonDumpRow
  ): Option[String] = {
    if (right._source.adult.exists(identity)) {
      None
    } else {
      right._source.externalIdsGrouped.get(ExternalSource.TheMovieDb)
    }
  }
}
