package com.teletracker.tasks

abstract class TeletrackerCompoundTask extends TeletrackerTask {
  def tasks: Seq[TeletrackerTask]

  override def runInternal(args: Args): Unit = {
    tasks.foreach(_.runInternal(args))
  }
}
