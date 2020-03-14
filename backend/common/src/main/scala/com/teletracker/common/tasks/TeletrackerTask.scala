package com.teletracker.common.tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.logging.TaskLogger
import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.util.EnvironmentDetection
import com.teletracker.common.util.Futures._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import javax.inject.Inject
import org.slf4j.{Logger, LoggerFactory}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.mutable
import scala.compat.java8.FutureConverters._
import scala.util.control.NonFatal

object TeletrackerTask {
  object CommonFlags {
    final val S3Logging = "s3Logging"
  }
}

trait TeletrackerTask extends Args {
  private var _logger: Logger = _
  private var _loggerCloseHook: () => Unit = () => {}

  protected def logger: Logger = _logger

  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _
  @Inject
  private[this] var s3: S3Client = _
  @Inject
  private[this] var taskScheduler: TaskScheduler = _

  private[this] var _options: Options = _

  type Args = Map[String, Option[Any]]
  type TypedArgs

  protected lazy val callbacks: mutable.Buffer[TaskCallback] =
    mutable.Buffer.empty

  private val preruns: mutable.Buffer[() => Unit] = mutable.Buffer.empty
  private val postruns: mutable.Buffer[Args => Unit] =
    mutable.Buffer.empty

  implicit protected def typedArgsEncoder: Encoder[TypedArgs]

  def preparseArgs(args: Args): TypedArgs

  def validateArgs(args: TypedArgs): Unit = {}

  def argsAsJson(args: Args): Json = preparseArgs(args).asJson

  protected def runInternal(args: Args): Unit

  protected def prerun(f: => Unit): Unit = {
    preruns += (() => f)
  }

  protected def postrun(f: Args => Unit): Unit = {
    postruns += f
  }

  private def init(args: Args): Unit = synchronized {
    val logToS3 =
      EnvironmentDetection.runningRemotely || args
        .valueOrDefault(TeletrackerTask.CommonFlags.S3Logging, false)

    if (logToS3) {
      val (s3Logger, onClose) = TaskLogger.make(
        getClass,
        s3,
        teletrackerConfig.data.s3_bucket,
        s"task-output/${getClass.getSimpleName}/${OffsetDateTime.now()}"
      )

      _logger = s3Logger
      _loggerCloseHook = onClose
    } else {
      _logger = LoggerFactory.getLogger(getClass)
    }

    _options = Options(
      scheduleFollowupTasks = args
        .value[Boolean]("scheduleFollowups")
        .orElse(args.value[Boolean]("scheduleFollowupTasks"))
        .getOrElse(false)
    )
  }

  final def run(args: Args): Unit = {
    try {
      init(args)

      registerFollowupTasksCallback()

      preruns.foreach(_())

      val parsedArgs = preparseArgs(args)

      validateArgs(parsedArgs)

      logger.info(s"Running ${getClass.getSimpleName} with args: ${args}")

      val success = try {
        runInternal(args)
        true
      } catch {
        case NonFatal(e) =>
          logger.error("Task ended unexpectedly", e)
          false
      }

      postruns.foreach(_(args))

      logger.info("Task completed. Checking for callbacks to run.")

      callbacks.foreach(cb => {
        if (success || cb.runOnFailure) {
          logger.info(s"Running callback: ${cb.name}")
          try {
            cb.cb(parsedArgs, args)
          } catch {
            case NonFatal(e) =>
              logger.error(s"""Callback "${cb.name}" failed.""", e)
          }
        }
      })
    } finally {
      if (_loggerCloseHook ne null) {
        _loggerCloseHook()
      }
    }
  }

  def registerCallback(cb: TaskCallback): Unit = {
    callbacks += cb
    logger.info(s"Successfully attached ${cb.name} callback")
  }

  protected def followupTasksToSchedule(
    args: TypedArgs,
    rawArgs: Args
  ): List[TeletrackerTaskQueueMessage] = Nil

  protected def options: Options = _options

  private def registerFollowupTasksCallback(): Unit = {
    registerCallback(
      TaskCallback(
        "scheduleFollowupTasks",
        (typedArgs, args) => {
          if (args.valueOrDefault("scheduleFollowups", true)) {
            val tasks = followupTasksToSchedule(typedArgs, args)

            if (tasks.isEmpty) {
              logger.info("No follow-up tasks to schedule.")
            }

            // TODO: Batch
            tasks.foreach(message => {
              logger.info(
                s"Scheduling follow-up task: ${message.toString}"
              )

              taskScheduler.schedule(message).await()
            })
          } else {
            logger.info("Skipping scheduling of followup jobs")
          }
        }
      )
    )
  }

  case class TaskCallback(
    name: String,
    cb: (TypedArgs, Args) => Unit,
    runOnFailure: Boolean = false)

  case class Options(scheduleFollowupTasks: Boolean = true)
}

trait TeletrackerTaskWithDefaultArgs extends TeletrackerTask with DefaultAnyArgs

trait DefaultAnyArgs { self: TeletrackerTask =>
  override type TypedArgs = Map[String, String]

  implicit override protected def typedArgsEncoder: Encoder[TypedArgs] =
    Encoder.encodeMap[String, String]

  override def preparseArgs(args: Args): TypedArgs = Map()

  override def argsAsJson(args: Args): Json = Json.Null
}
