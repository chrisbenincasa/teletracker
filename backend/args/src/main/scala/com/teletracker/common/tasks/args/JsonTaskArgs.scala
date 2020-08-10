package com.teletracker.common.tasks.args

import io.circe.Json

object JsonTaskArgs {
  def extractArgs(args: Map[String, Json]): Map[String, Any] = {
    args.mapValues(extractValue)
  }

  private def extractValue(j: Json): Any = {
    j.fold(
      None,
      identity,
      x => x.toDouble,
      identity,
      v => v.map(extractValue),
      o => o.toMap.mapValues(extractValue)
    )
  }
}
