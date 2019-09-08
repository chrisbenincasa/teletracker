package com.teletracker.tasks

import com.teletracker.common.inject.{AsyncDbProvider, SyncDbProvider}

object TeletrackerTaskRunner extends TeletrackerTaskApp[NoopTeletrackerTask] {
  val clazz = flag[String]("class", "The Teletracker task to run")

  lazy val loadedClass = Class.forName(clazz())

  override protected def allowUndefinedFlags: Boolean =
    true

  override protected def run(): Unit = {
    try {
      if (!classOf[TeletrackerTask].isAssignableFrom(loadedClass)) {
        throw new IllegalArgumentException(
          "Specified class if not a subclass of TeletrackerTask!"
        )
      }

      injector
        .instance(loadedClass)
        .asInstanceOf[TeletrackerTask]
        .run(collectArgs)
    } finally {
      injector.instance[SyncDbProvider].shutdown()
      injector.instance[AsyncDbProvider].shutdown()
    }
  }

  override protected def collectArgs: Map[String, Option[Any]] = {
    args.toList
      .map(arg => {
        val Array(f, value) = arg.split("=", 2)
        f.stripPrefix("-") -> Some(value)
      })
      .toMap
  }
}
