package com.teletracker.tasks.general

import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.util.Concatenator
import javax.inject.Inject
import java.net.URI

class ConcatenateTask @Inject()(concatenator: Concatenator)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("source")
    val destination = args.valueOrThrow[URI]("destination")

    concatenator.concatenate(input, destination)
  }
}
