package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.tasks.util.Concatenator
import javax.inject.Inject
import java.net.URI

class ConcatenateTask @Inject()(concatenator: Concatenator)
    extends UntypedTeletrackerTask {

  override def preparseArgs(args: RawArgs): ArgsType = {
    Map(
      "source" -> args.valueOrThrow[String]("source"),
      "destination" -> args.valueOrThrow[String]("destination")
    )
  }

  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("source")
    val destination = rawArgs.valueOrThrow[URI]("destination")

    concatenator.concatenate(input, destination)
  }
}
