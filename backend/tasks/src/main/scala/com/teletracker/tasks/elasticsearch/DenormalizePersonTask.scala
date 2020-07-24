package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedPersonUpdater
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.model.DenormalizePersonTaskArgs
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DenormalizePersonTask @Inject()(
  denormalizedPersonUpdater: DenormalizedPersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DenormalizePersonTaskArgs] {
  override def retryable: Boolean = true

  override protected def runInternal(): Unit = {
    denormalizedPersonUpdater
      .fullyDenormalizePerson(args.personId, args.dryRun)
      .await()
  }
}
