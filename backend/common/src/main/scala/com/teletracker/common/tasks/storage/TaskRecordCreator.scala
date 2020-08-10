package com.teletracker.common.tasks.storage

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.tasks.TeletrackerTask.JsonableArgs
import io.circe.Json
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import scala.io.Source
import scala.util.Try

@Singleton
class TaskRecordCreator @Inject()() {
  private val versionMetadata = loadMetadataFile()

  def createScheduled(
    id: UUID,
    task: String,
    args: Map[String, Json]
  ): TaskRecord = {
    TaskRecord(
      id = id,
      taskName = task,
      fullTaskName = None,
      args = args.asJson,
      status = TaskStatus.Scheduled,
      createdAt = Some(Instant.now()),
      startedAt = None,
      finishedAt = None,
      teletrackerVersion = versionMetadata.flatMap(_.version),
      gitSha = versionMetadata.flatMap(_.gitSha),
      logUri = None,
      hostname = Option(System.getenv("HOSTNAME"))
        .filter(_.nonEmpty)
        .orElse(Try(InetAddress.getLocalHost.getHostAddress).toOption)
    )
  }

  def create[T <: TeletrackerTask](
    id: UUID,
    task: T,
    args: T#ArgsType,
    status: TaskStatus
  )(implicit typedArgs: JsonableArgs[T#ArgsType]
  ): TaskRecord = {
    createGen(id, task, args, status)
  }

  def create[T <: TeletrackerTask](
    id: UUID,
    task: T,
    args: Map[String, String],
    status: TaskStatus
  )(implicit typedArgs: JsonableArgs[Map[String, String]]
  ): TaskRecord = {
    createGen(id, task, args, status)
  }

  def createGen[T <: TeletrackerTask, U](
    id: UUID,
    task: T,
    args: U,
    status: TaskStatus
  )(implicit typedArgs: JsonableArgs[U]
  ): TaskRecord = {
    val clazz = task.getClass
    TaskRecord(
      id = id,
      taskName = clazz.getSimpleName,
      fullTaskName = Some(clazz.getName),
      args = typedArgs.asJson(args),
      status = status,
      createdAt = Some(Instant.now()),
      startedAt = None,
      finishedAt = None,
      teletrackerVersion = versionMetadata.flatMap(_.version),
      gitSha = versionMetadata.flatMap(_.gitSha),
      logUri = Some(task.remoteLogLocation),
      hostname = Option(System.getenv("HOSTNAME"))
        .filter(_.nonEmpty)
        .orElse(Try(InetAddress.getLocalHost.getHostName).toOption)
    )
  }

  private def loadMetadataFile() = {
    Try {
      val source = Source.fromFile(
        getClass.getClassLoader.getResource("version_info.txt").getFile
      )
      try {
        val keyValues = source
          .getLines()
          .flatMap(line => {
            Try(line.split("=", 2) match {
              case Array(key, value) => key -> value
            }).toOption
          })
          .toMap

        VersionMetadata(
          builtAt = keyValues.get("BUILT_AT").map(Instant.parse(_)),
          version = keyValues.get("VERSION"),
          gitSha = keyValues.get("GIT_SHA")
        )
      } finally {
        source.close()
      }
    }.toOption
  }
}

case class VersionMetadata(
  builtAt: Option[Instant],
  version: Option[String],
  gitSha: Option[String])
