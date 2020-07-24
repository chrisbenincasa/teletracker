package com.teletracker.tasks

import com.google.inject.{Injector, Module}
import com.teletracker.common.tasks.model.TeletrackerTaskIdentifier
import com.teletracker.common.tasks.storage.{
  TaskRecordCreator,
  TaskRecordStore,
  TaskStatus
}
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.tasks.args.{JsonTaskArgs}
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.inject.TaskSchedulerModule
import io.circe.Json
import javax.inject.Inject
import java.time.Instant
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}

object TeletrackerTaskRunner extends TeletrackerTaskApp[NoopTeletrackerTask] {
  val clazz = flag[String]("class", "", "The Teletracker task class to run")
  val taskName = flag[String]("task", "", "The Teletracker task name to run")

  @volatile private var _instance: TeletrackerTaskRunner = _

  def instance: TeletrackerTaskRunner = _instance

  override protected def overrideModules: Seq[Module] =
    Seq(new TaskSchedulerModule)

  override protected def allowUndefinedFlags: Boolean =
    true

  override protected def postInjectorStartup(): Unit = {
    _instance = new TeletrackerTaskRunner(injector.underlying)
  }

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

    val task = _instance.getInstance(clazzToRun)
    val args = collectArgs
    val strigifiedArgs = args.collect {
      case (str, option) if option.isDefined => str -> option.get.toString
    }

    val recordCreator = injector.instance[TaskRecordCreator]
    val recordStore = injector.instance[TaskRecordStore]

    val record = recordCreator
      .create(task.taskId, task, strigifiedArgs, TaskStatus.Executing)
      .copy(
        startedAt = Some(Instant.now())
      )

    recordStore
      .recordNewTask(record)
      .await()

    _instance.runFromString(clazzToRun, collectArgs) match {
      case TeletrackerTask.SuccessResult =>
        recordStore.setTaskSuccess(record).await()
      case TeletrackerTask.FailureResult(_) =>
        recordStore.setTaskFailed(record).await()
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
  ): Option[Class[_ <: TeletrackerTask]] = {
    TeletrackerTaskIdentifier.forString(taskName).asScala match {
      case Some(value) =>
        Some(TaskRegistry.taskForTaskType(value))
      case None =>
        TaskRegistry.TasksToClass.get(taskName)
    }

  }

  def runFromString(
    clazz: String,
    args: Map[String, Option[Any]]
  ): TeletrackerTask.TaskResult = {
    run(getInstance(clazz), args)
  }

  def runFromJsonArgs(
    clazz: String,
    args: Map[String, Json]
  ): TeletrackerTask.TaskResult = {
    runFromString(clazz, JsonTaskArgs.extractArgs(args))
  }

  def run(
    task: TeletrackerTask,
    args: Map[String, Option[Any]]
  ): TeletrackerTask.TaskResult = {
    task.run(args)
  }

}
