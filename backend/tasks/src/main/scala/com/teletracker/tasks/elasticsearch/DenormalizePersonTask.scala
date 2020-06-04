package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedPersonUpdater
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.model.DenormalizePersonTaskArgs
import com.teletracker.common.tasks.{model, TypedTeletrackerTask}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class DenormalizePersonTask @Inject()(
  denormalizedPersonUpdater: DenormalizedPersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DenormalizePersonTaskArgs] {
  override def retryable: Boolean = true

  override def preparseArgs(args: RawArgs): DenormalizePersonTaskArgs =
    model.DenormalizePersonTaskArgs(
      personId = args.valueOrThrow[UUID]("personId"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  override protected def runInternal(): Unit = {
    denormalizedPersonUpdater
      .fullyDenormalizePerson(args.personId, args.dryRun)
      .await()
  }
}
