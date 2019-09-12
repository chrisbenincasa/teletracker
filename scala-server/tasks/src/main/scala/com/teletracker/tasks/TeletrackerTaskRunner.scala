package com.teletracker.tasks

import com.google.inject.{Injector, TypeLiteral}
import com.teletracker.common.db.{
  AsyncDbProvider,
  BaseDbProvider,
  SyncDbProvider
}
import javax.inject.Inject
import scala.collection.JavaConverters._

object TeletrackerTaskRunner extends TeletrackerTaskApp[NoopTeletrackerTask] {
  val clazz = flag[String]("class", "The Teletracker task to run")

  override protected def allowUndefinedFlags: Boolean =
    true

  override protected def run(): Unit = {
    try {
      new TeletrackerTaskRunner(injector.underlying).run(clazz(), collectArgs)
    } finally {
      injector.underlying
        .findBindingsByType(new TypeLiteral[BaseDbProvider]() {})
        .asScala
        .foreach(_.getProvider.get().shutdown())
//      injector.instance[SyncDbProvider].shutdown()
//      injector.instance[AsyncDbProvider].shutdown()
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

class TeletrackerTaskRunner @Inject()(injector: Injector) {
  def getInstance(clazz: String): TeletrackerTask = {
    val loadedClass = Class.forName(clazz)
    if (!classOf[TeletrackerTask].isAssignableFrom(loadedClass)) {
      throw new IllegalArgumentException(
        "Specified class if not a subclass of TeletrackerTask!"
      )
    }

    injector
      .getInstance(loadedClass)
      .asInstanceOf[TeletrackerTask]
  }

  def run(
    clazz: String,
    args: Map[String, Option[Any]]
  ): Unit = {
    getInstance(clazz)
      .run(args)
  }
}
