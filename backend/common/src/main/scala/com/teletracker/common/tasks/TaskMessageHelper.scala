package com.teletracker.common.tasks

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.model.TeletrackerTaskIdentifier
import io.circe.Encoder
import io.circe.syntax._
import scala.reflect.ClassTag

object TaskMessageHelper {
  def forTaskArgs[T: ClassTag: Encoder.AsObject](
    taskIdentifier: TeletrackerTaskIdentifier,
    args: T,
    tags: Option[Set[String]]
  ): TeletrackerTaskQueueMessage = {
    TeletrackerTaskQueueMessage(
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
      clazz = ct.runtimeClass.getName,
      args = args.asJsonObject.toMap,
      jobTags = tags
    )
  }
}
