package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DenormalizeItemTask @Inject()(
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DenormalizeItemTaskArgs] {
  override protected def runInternal(): Unit = {
    denormalizedItemUpdater.fullyDenormalizeItem(args).await()
  }
}
