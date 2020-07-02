package com.teletracker.common.tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.logging.TaskLogger
import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.EnvironmentDetection
import com.teletracker.common.util.Futures._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import javax.inject.Inject
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object TeletrackerTask {
  object CommonFlags {
    final val S3Logging = "s3Logging"
    final val OutputToConsole = "outputToConsole"
    final val ScheduleFollowups = "scheduleFollowups" // Legacy
    final val ScheduleFollowupTasks = "scheduleFollowupTasks"
  }

  object TaskResult {
    def success: TaskResult = SuccessResult
    def failure(e: Throwable): TaskResult = FailureResult(e)
  }

  sealed trait TaskResult {
    def isSuccess: Boolean
  }

  case object SuccessResult extends TaskResult {
    override def isSuccess: Boolean = true
  }

  case class FailureResult(error: Throwable) extends TaskResult {
    override def isSuccess: Boolean = false
  }

  trait JsonableArgs[T] {
    def asJson(a: T): Json
  }

  object JsonableArgs {
    implicit def jsonableEither[L, R](
      implicit l: JsonableArgs[L],
      r: JsonableArgs[R]
    ): JsonableArgs[Either[L, R]] =
      new JsonableArgs[Either[L, R]] {
        override def asJson(a: Either[L, R]): Json = a match {
          case Left(value)  => l.asJson(value)
          case Right(value) => r.asJson(value)
        }
      }

    implicit val mapJsonableArgs: JsonableArgs[Map[String, String]] =
      new JsonableArgs[Map[String, String]] {
        override def asJson(a: Map[String, String]): Json = a.asJson
      }

    implicit def jsonableArgs[T: Encoder]: JsonableArgs[T] =
      new JsonableArgs[T] {
        override def asJson(a: T): Json = a.asJson
      }
  }

  def taskMessage[T <: TeletrackerTask](
    args: T#ArgsType,
    tags: Option[Set[String]] = None
  )(implicit ct: ClassTag[T],
    enc: Encoder.AsObject[T#ArgsType]
  ): TeletrackerTaskQueueMessage = {
    TeletrackerTaskQueueMessage(
      id = Some(UUID.randomUUID()),
      clazz = ct.runtimeClass.getName,
      args = args.asJsonObject.toMap,
      jobTags = tags
    )
  }

  type RawArgs = Map[String, Any]
}

trait TeletrackerTask extends TaskArgImplicits {
  import TeletrackerTask._

  type ArgsType <: AnyRef

  private[this] var didInit = false
  protected[this] var _taskId: UUID = UUID.randomUUID()

  def taskId: UUID = _taskId
  def taskId_=(taskId: UUID): Unit = _taskId = taskId

  private[this] val selfLogger = LoggerFactory.getLogger(getClass)

  private var _logger: Logger = _
  private var _loggerCloseHook: () => Unit = () => {}

  protected def logger: Logger = _logger

  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _
  @Inject
  private[this] var s3: S3Client = _
  @Inject
  private[this] var taskScheduler: TaskScheduler = _

  private[this] var _registeredFollowupTasks
    : mutable.Buffer[TeletrackerTaskQueueMessage] =
    mutable.Buffer.empty

  protected def registeredFollowupTasks: List[TeletrackerTaskQueueMessage] =
    _registeredFollowupTasks.toList

  private[this] var _options: Options = _

  private def checkInit() = {
    if (!didInit) {
      throw new IllegalStateException(
        "Cannot access args before initialization"
      )
    }
  }

  private[this] var _rawArgs: RawArgs = _
  protected def rawArgs: RawArgs = {
    checkInit()
    assert(_rawArgs ne null) // This should be impossible
    _rawArgs
  }

  private[this] var _args: ArgsType = _
  protected def args: ArgsType = {
    checkInit()
    assert(_args ne null) // This should be impossible
    _args
  }

  protected lazy val callbacks: mutable.Buffer[TaskCallback] =
    mutable.Buffer.empty

  private val preruns: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val postruns: mutable.Buffer[RawArgs => Unit] =
    mutable.Buffer.empty

  def preparseArgs(args: RawArgs): ArgsType

  def validateArgs(args: ArgsType): Unit = {}

  def argsAsJson(args: RawArgs): Json // = typedArgs.asJson(preparseArgs(args))

  def retryable: Boolean = false

  def remoteLogLocation: URI =
    URI.create(s"s3://${teletrackerConfig.data.s3_bucket}/$s3LogKey")

  private lazy val s3LogKey =
    s"task-output/${getClass.getSimpleName}/${LocalDate.now()}/${OffsetDateTime.now()}"

  protected def runInternal(): Unit

  protected def prerun(f: => Unit): Unit = {
    preruns += (() => f)
  }

  protected def postrun(f: RawArgs => Unit): Unit = {
    postruns += f
  }

  private def init(args: RawArgs): Unit = synchronized {
    if (!didInit) {
      didInit = true

      _rawArgs = args
      _args = preparseArgs(args)

      val logToS3 =
        EnvironmentDetection.runningRemotely || args
          .valueOrDefault(CommonFlags.S3Logging, false)

      val logToConsole = args
        .valueOrDefault(CommonFlags.OutputToConsole, true)

      if (logToS3) {
        val (s3Logger, onClose) = TaskLogger.make(
          getClass,
          s3,
          teletrackerConfig.data.s3_bucket,
          s3LogKey,
          outputToConsole = logToConsole
        )

        selfLogger.info(
          s"Logs for ${getClass.getSimpleName} (id: $taskId) can be found at s3://${teletrackerConfig.data.s3_bucket}/$s3LogKey"
        )

        _logger = s3Logger
        _loggerCloseHook = onClose
      } else {
        _logger = LoggerFactory.getLogger(getClass)
      }

      _options = Options(
        scheduleFollowupTasks = args
          .value[Boolean](CommonFlags.ScheduleFollowups)
          .orElse(args.value[Boolean](CommonFlags.ScheduleFollowupTasks))
          .getOrElse(true)
      )
    }

  }

  final def run(args: RawArgs): TeletrackerTask.TaskResult = {
    try {
      init(args)

      validateArgs(this.args)

      logger.info(
        s"Running ${getClass.getSimpleName} (id: $taskId) with args: ${args}"
      )

      registerFollowupTasksCallback()

      preruns.foreach(_())

      val result = try {
        runInternal()
        TaskResult.success
      } catch {
        case NonFatal(e) =>
          logger.error("Task ended unexpectedly", e)
          TaskResult.failure(e)
      }

      postruns.foreach(_(args))

      logger.info("Task completed. Checking for callbacks to run.")

      callbacks.foreach(cb => {
        if (result.isSuccess || cb.runOnFailure) {
          logger.debug(s"Running callback: ${cb.name}")
          try {
            cb.cb(this.args, this.rawArgs)
          } catch {
            case NonFatal(e) =>
              logger.error(s"""Callback "${cb.name}" failed.""", e)
          }
        }
      })

      result
    } finally {
      if (_loggerCloseHook ne null) {
        _loggerCloseHook()
      }
    }
  }

  def registerCallback(cb: TaskCallback): Unit = {
    callbacks += cb
    logger.debug(s"Successfully attached ${cb.name} callback")
  }

  protected def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] =
    Nil

  protected def registerFollowupTask(task: TeletrackerTaskQueueMessage): Unit =
    _registeredFollowupTasks.synchronized {
      _registeredFollowupTasks += task
    }

  protected def options: Options = _options

  private def registerFollowupTasksCallback(): Unit = {
    registerCallback(
      TaskCallback(
        "scheduleFollowupTasks",
        (typedArgs, args) => {
          if (options.scheduleFollowupTasks) {
            val tasks = followupTasksToSchedule() ++ registeredFollowupTasks

            if (tasks.isEmpty) {
              logger.debug("No follow-up tasks to schedule.")
            }

            tasks.foreach(message => {
              logger.debug(
                s"Scheduling follow-up task: ${message.toString}"
              )
            })

            taskScheduler
              .schedule(tasks.map(message => message -> Some(message.clazz)))
              .await()
          } else {
            logger.debug("Skipping scheduling of followup jobs")
          }
        }
      )
    )
  }

  case class TaskCallback(
    name: String,
    cb: (ArgsType, RawArgs) => Unit,
    runOnFailure: Boolean = false)

  case class Options(scheduleFollowupTasks: Boolean = true)
}

trait UntypedTeletrackerTask extends TeletrackerTask with DefaultAnyArgs

trait DefaultAnyArgs { self: TeletrackerTask =>
  override type ArgsType = Map[String, String]

  override def preparseArgs(args: RawArgs): Map[String, String] =
    args.collect {
      case (k, Some(v)) => k -> v.toString
    }

  override def argsAsJson(args: RawArgs): Json = preparseArgs(args).asJson
}

abstract class TypedTeletrackerTask[_ArgsType <: AnyRef](
  implicit jsonArgs: JsonableArgs[_ArgsType])
    extends TeletrackerTask {
  override type ArgsType = _ArgsType

  override def argsAsJson(args: RawArgs): Json =
    jsonArgs.asJson(preparseArgs(args))
}
