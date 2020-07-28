package com.teletracker.common.tasks.args

import io.circe.Json

object JsonTaskArgs {
  def extractArgs(args: Map[String, Json]): Map[String, Any] = {
    args.mapValues(extractValue)
  }

  private def extractValue(j: Json): Any = {
    j.fold(
      None,
      Some(_),
      x => Some(x.toDouble),
      Some(_),
      v => Some(v.map(extractValue)),
      o => Some(o.toMap.mapValues(extractValue))
    )
  }
}
