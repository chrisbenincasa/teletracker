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
        try {
          val Array(f, value) = arg.split("=", 2)
          f.stripPrefix("-") -> Some(value)
        } catch {
          case e: MatchError =>
            println(
              s"Could not match arg split. Actual: ${arg.split("=").toList}"
            )
            throw e
        }
      })
      .toMap
  }
}

class TeletrackerTaskRunner @Inject()(injector: Injector) {
  def getInstance(clazz: String): TeletrackerTask = {
    val loadedClass = {
      Try(Class.forName(clazz)) match {
        case Failure(_: ClassNotFoundException) =>
          val shorthand = attemptToLoadTaskName(clazz)
          if (shorthand.isEmpty) {
            throw new IllegalArgumentException(
              s"$clazz is either not a class that exists or not a shorthand task name"
            )
          } else {
            shorthand
          }

        case Failure(ex) => throw ex

        case Success(value)
            if !classOf[TeletrackerTask].isAssignableFrom(value) =>
          val shorthand = attemptToLoadTaskName(clazz)
          if (shorthand.isEmpty) {
            throw new IllegalArgumentException(
              s"$clazz is either not a TeletrackerTask subclass, or not a shorthand task name"
            )
          } else {
            shorthand
          }

        case Success(value) =>
          Some(value)
      }
    }

    if (loadedClass.isEmpty) {
      throw new RuntimeException(
        s"Could not load class with name: ${clazz}"
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
      .run(args)
  }
}
