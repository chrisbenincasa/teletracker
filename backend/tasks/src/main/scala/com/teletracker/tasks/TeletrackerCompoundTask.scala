package com.teletracker.tasks

import com.teletracker.common.tasks.TeletrackerTask

abstract class TeletrackerCompoundTask extends TeletrackerTask {
  def tasks: Seq[TeletrackerTask]

  override def runInternal(args: Args): Unit = {
    tasks.foreach(_.run(args))
  }
}
