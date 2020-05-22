package com.teletracker.tasks.elasticsearch

import com.teletracker.common.elasticsearch.denorm.DenormalizedPersonUpdater
import com.teletracker.common.tasks.model.DenormalizePersonTaskArgs
import com.teletracker.common.tasks.{model, TeletrackerTask}
import com.teletracker.common.util.Futures._
import io.circe.Encoder
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class DenormalizePersonTask @Inject()(
  denormalizedPersonUpdater: DenormalizedPersonUpdater
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override type TypedArgs = DenormalizePersonTaskArgs

  override def retryable: Boolean = true

  implicit override protected def typedArgsEncoder
    : Encoder[DenormalizePersonTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): DenormalizePersonTaskArgs =
    model.DenormalizePersonTaskArgs(
      personId = args.valueOrThrow[UUID]("personId"),
      dryRun = args.valueOrDefault("dryRun", true)
    )

  override protected def runInternal(_args: Args): Unit = {
    val args = preparseArgs(_args)

    denormalizedPersonUpdater
      .fullyDenormalizePerson(args.personId, args.dryRun)
      .await()
  }
}
