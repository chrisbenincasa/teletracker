package com.teletracker.tasks.util

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.TeletrackerTask
import io.circe.Encoder
import io.circe._
import io.circe.syntax._
import scala.reflect.ClassTag

object TaskMessageHelper {
  def forTask[T <: TeletrackerTask](
    args: T#TypedArgs,
    tags: Option[Set[String]] = None
  )(implicit ct: ClassTag[T],
    enc: Encoder.AsObject[T#TypedArgs]
  ) = {
    TeletrackerTaskQueueMessage(
      clazz = ct.runtimeClass.getName,
      args = args.asJsonObject.toMap,
      jobTags = tags
    )
  }
}
