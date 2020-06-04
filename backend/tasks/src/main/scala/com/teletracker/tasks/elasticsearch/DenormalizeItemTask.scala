package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.common.tasks.{model, TypedTeletrackerTask}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class DenormalizeItemTask @Inject()(
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DenormalizeItemTaskArgs] {
  override def preparseArgs(args: RawArgs): DenormalizeItemTaskArgs =
    model.DenormalizeItemTaskArgs(
      itemId = args.valueOrThrow[UUID]("itemId"),
      creditsChanged = args.valueOrThrow[Boolean]("creditsChanged"),
      crewChanged = args.valueOrThrow[Boolean]("crewChanged"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  override protected def runInternal(): Unit = {
    denormalizedItemUpdater.fullyDenormalizeItem(args).await()
  }
}
