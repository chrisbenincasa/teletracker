package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.common.tasks.{model, TeletrackerTask}
import com.teletracker.common.util.Futures._
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class DenormalizeItemTask @Inject()(
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override type TypedArgs = DenormalizeItemTaskArgs

  implicit override protected def typedArgsEncoder
    : Encoder[DenormalizeItemTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): DenormalizeItemTaskArgs =
    model.DenormalizeItemTaskArgs(
      itemId = args.valueOrThrow[UUID]("itemId"),
      creditsChanged = args.valueOrThrow[Boolean]("creditsChanged"),
      crewChanged = args.valueOrThrow[Boolean]("crewChanged"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  override protected def runInternal(_args: Args): Unit = {
    val args = preparseArgs(_args)

    denormalizedItemUpdater.fullyDenormalizeItem(args).await()
  }
}
