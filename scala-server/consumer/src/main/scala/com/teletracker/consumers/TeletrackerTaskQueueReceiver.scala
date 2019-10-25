//package com.teletracker.consumers
//
//import com.google.cloud.pubsub.v1.{AckReplyConsumer, MessageReceiver}
//import com.google.pubsub.v1.PubsubMessage
//import com.teletracker.common.pubsub.{JobTags, TeletrackerTaskQueueMessage}
//import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskRunner}
//import io.circe.Json
//import javax.inject.Inject
//import org.slf4j.LoggerFactory
//import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
//import scala.util.control.NonFatal
//import scala.collection.JavaConverters._
//import scala.collection.mutable.ListBuffer
//
//class TeletrackerTaskQueueReceiver @Inject()(taskRunner: TeletrackerTaskRunner)
//    extends MessageReceiver {
//  import io.circe.parser._
//
//  private val logger = LoggerFactory.getLogger(getClass)
//
//  private val needsTmdbPool = new JobPool("TmdbJobs", 1)
//  private val normalPool = new JobPool("NormalJobs", 2)
//
//  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
//    override def run(): Unit = {
//      (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
//    }
//  }))
//
//  def getUnexecutedTasks: Iterable[TeletrackerTaskQueueMessage] = {
//    (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
//  }
//
//  override def receiveMessage(
//    message: PubsubMessage,
//    consumer: AckReplyConsumer
//  ): Unit = {
//    val messageString = new String(message.getData.toByteArray)
//    parse(messageString).flatMap(_.as[TeletrackerTaskQueueMessage]) match {
//      case Left(e) =>
//        logger.error(s"Could not parse message: $messageString", e)
//        consumer.ack()
//
//      case Right(message) =>
//        try {
//          val task = taskRunner.getInstance(message.clazz)
//          val runnable =
//            new TeletrackerTaskRunnable(
//              message,
//              task,
//              extractArgs(message.args)
//            )
//
//          logger.info(s"Attempting to schedule ${message.clazz}")
//
//          if (message.jobTags
//                .getOrElse(Set.empty)
//                .contains(JobTags.RequiresTmdbApi)) {
//            needsTmdbPool.submit(runnable)
//          } else {
//            normalPool.submit(runnable)
//          }
//        } catch {
//          case NonFatal(e) =>
//            logger.error(
//              s"Unexpected error while handling message: ${messageString}",
//              e
//            )
//        } finally {
//          consumer.ack()
//        }
//    }
//  }
//
//  private def extractArgs(args: Map[String, Json]): Map[String, Option[Any]] = {
//    args.mapValues(extractValue)
//  }
//
//  private def extractValue(j: Json): Option[Any] = {
//    j.fold(
//      None,
//      Some(_),
//      x => Some(x.toDouble),
//      Some(_),
//      v => Some(v.map(extractValue)),
//      o => Some(o.toMap.mapValues(extractValue))
//    )
//  }
//}
//
