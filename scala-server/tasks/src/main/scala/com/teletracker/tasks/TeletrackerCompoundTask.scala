package com.teletracker.tasks

abstract class TeletrackerCompoundTask extends TeletrackerTask {
  def tasks: Seq[TeletrackerTask]

  override def run(args: Args): Unit = {
    tasks.foreach(_.run(args))
  }
}
