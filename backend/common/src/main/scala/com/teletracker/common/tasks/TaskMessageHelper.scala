package com.teletracker.common.tasks

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.model.TeletrackerTaskIdentifier
import io.circe.Encoder
import io.circe.syntax._
import java.util.UUID
import scala.reflect.ClassTag

object TaskMessageHelper {
  final val MessageGroupId = "default"

  def forTaskArgs[T: ClassTag: Encoder.AsObject](
    taskIdentifier: TeletrackerTaskIdentifier,
    args: T,
    tags: Option[Set[String]]
  ): TeletrackerTaskQueueMessage = {
    TeletrackerTaskQueueMessage(
      id = Some(UUID.randomUUID()),
      clazz = taskIdentifier.identifier(),
      args = args.asJsonObject.toMap,
      jobTags = tags
    )
  }

  def forTask[T <: TeletrackerTask](
    args: T#TypedArgs,
    tags: Option[Set[String]] = None
  )(implicit ct: ClassTag[T],
    enc: Encoder.AsObject[T#TypedArgs]
  ): TeletrackerTaskQueueMessage = {
    TeletrackerTaskQueueMessage(
      id = Some(UUID.randomUUID()),
      clazz = ct.runtimeClass.getName,
      args = args.asJsonObject.toMap,
      jobTags = tags
    )
  }
}
