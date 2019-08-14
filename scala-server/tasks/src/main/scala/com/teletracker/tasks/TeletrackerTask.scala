package com.teletracker.tasks

import com.teletracker.tasks.util.Args

trait TeletrackerTask extends Args {
  type Args = Map[String, Option[Any]]

  def preparseArgs(args: Args): Unit = {}
  def run(): Unit = run(Map.empty)
  def run(args: Args): Unit
}
