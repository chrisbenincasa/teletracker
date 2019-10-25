package com.teletracker.tasks

import com.google.inject.Injector
import com.teletracker.common.db.BaseDbProvider
import javax.inject.Inject
import scala.util.{Failure, Success, Try}

object TeletrackerTaskRunner extends TeletrackerTaskApp[NoopTeletrackerTask] {
  val clazz = flag[String]("class", "", "The Teletracker task class to run")
  val taskName = flag[String]("task", "", "The Teletracker task name to run")

  override protected def allowUndefinedFlags: Boolean =
    true

  override protected def run(): Unit = {
    require(clazz().nonEmpty || taskName().nonEmpty)

    val clazzToRun = if (taskName().nonEmpty) {
      TaskRegistry.TasksToClass
        .getOrElse(
          taskName(),
          throw new IllegalArgumentException(
            s"No task with the name ${taskName()}"
          )
        )
        .getName
    } else {
      clazz()
    }

    try {
      new TeletrackerTaskRunner(injector.underlying)
        .run(clazzToRun, collectArgs)
    } finally {
      injector.instance[BaseDbProvider].shutdown()
    }

    System.exit(0)
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
    val loadedClass = {
      Try(Class.forName(clazz)) match {
        case Failure(_: ClassNotFoundException) =>
          attemptToLoadTaskName(clazz)

        case Failure(ex) => throw ex

        case Success(value)
            if !classOf[TeletrackerTask].isAssignableFrom(value) =>
          attemptToLoadTaskName(clazz)

        case Success(value) =>
          Some(value)
      }
    }

    if (loadedClass.isEmpty) {
      throw new IllegalArgumentException(
        "Specified class if not a subclass of TeletrackerTask!"
      )
    }

    injector
      .getInstance(loadedClass.get)
      .asInstanceOf[TeletrackerTask]
  }

  private def attemptToLoadTaskName(
    taskName: String
  ): Option[Class[_ <: TeletrackerTask]] =
    TaskRegistry.TasksToClass.get(taskName)

  def run(
    clazz: String,
    args: Map[String, Option[Any]]
  ): Unit = {
    getInstance(clazz)
      .runInternal(args)
  }
}
