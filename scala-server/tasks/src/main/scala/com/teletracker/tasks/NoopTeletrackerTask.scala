package com.teletracker.tasks

class NoopTeletrackerTask extends TeletrackerTask {
  override def run(args: Args): Unit = println(args)
}

class TimeoutTask extends TeletrackerTask {
  override def run(args: Args): Unit = {
    val timeout = args.valueOrDefault("timeout", 1000)
    Thread.sleep(timeout)
  }
}
