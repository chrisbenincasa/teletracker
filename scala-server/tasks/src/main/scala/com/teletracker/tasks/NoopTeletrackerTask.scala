package com.teletracker.tasks

class NoopTeletrackerTask extends TeletrackerTask {
  override def run(args: Args): Unit = println(args)
}
