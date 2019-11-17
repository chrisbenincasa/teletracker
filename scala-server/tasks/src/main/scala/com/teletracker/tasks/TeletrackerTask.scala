package com.teletracker.tasks

import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.util.Args
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

trait TeletrackerTask extends Args {
  private val logger = LoggerFactory.getLogger(getClass)

  type Args = Map[String, Option[Any]]
  type TypedArgs

  protected lazy val callbacks: mutable.Buffer[TaskCallback] =
    mutable.Buffer.empty

  private val preruns: mutable.Buffer[() => Unit] = mutable.Buffer.empty

  implicit protected def typedArgsEncoder: Encoder[TypedArgs]

  def preparseArgs(args: Args): TypedArgs
  def argsAsJson(args: Args): Json = preparseArgs(args).asJson

  protected def runInternal(): Unit = runInternal(Map.empty)
  protected def runInternal(args: Args): Unit

  protected def prerun(f: => Unit): Unit = {
    preruns += (() => f)
  }

  final def run(args: Args): Unit = {
    preruns.foreach(_())

    val parsedArgs = preparseArgs(args)

    val success = try {
      runInternal(args)
      true
    } catch {
      case NonFatal(e) =>
        logger.error("Task ended unexpectedly", e)
        false
    }

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
  }

  def registerCallback(cb: TaskCallback): Unit = {
    callbacks += cb
    logger.info(s"Successfully attached ${cb.name} callback")
  }

  case class TaskCallback(
    name: String,
    cb: (TypedArgs, Args) => Unit,
    runOnFailure: Boolean = false)
}

trait TeletrackerTaskWithDefaultArgs extends TeletrackerTask with DefaultAnyArgs

trait DefaultAnyArgs { self: TeletrackerTask =>
  override type TypedArgs = Map[String, String]

  implicit override protected def typedArgsEncoder: Encoder[TypedArgs] =
    Encoder.encodeMap[String, String]

  override def preparseArgs(args: Args): TypedArgs = Map()

  override def argsAsJson(args: Args): Json = Json.Null
}

trait SchedulesFollowupTasks { self: TeletrackerTask =>

  private val logger = LoggerFactory.getLogger(getClass)

  protected def publisher: SqsClient

  registerCallback(
    TaskCallback(
      "scheduleFollowupTasks",
      (typedArgs, args) => {
        if (args.valueOrDefault("scheduleFollowups", true)) {
          val tasks = followupTasksToSchedule(typedArgs)
          logger.info(
            s"Scheduling ${tasks.size} follow-up tasks:\n${tasks.map(_.toString).mkString("\n")}"
          )

          // TODO: Batch
          tasks.foreach(message => {
            publisher
              .sendMessage(
                SendMessageRequest
                  .builder()
                  .messageBody(message.asJson.noSpaces)
                  .queueUrl(
                    "https://sqs.us-west-1.amazonaws.com/302782651551/teletracker-tasks-qa"
                  )
                  .build()
              )
          })
        } else {
          logger.info("Skipping scheduling of followup jobs")
        }
      }
    )
  )

  def followupTasksToSchedule(
    args: TypedArgs
  ): List[TeletrackerTaskQueueMessage]
}
