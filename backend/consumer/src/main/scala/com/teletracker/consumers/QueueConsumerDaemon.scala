package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.aws.sqs.SqsQueueListener
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase
import com.teletracker.consumers.impl.{
  EsDenormalizeItemWorker,
  EsIngestQueueWorker,
  TaskQueueWorker
}
import com.teletracker.consumers.inject.{HttpClientModule, Modules}
import com.twitter.app.Flaggable
import com.twitter.util.Await
import scala.concurrent.ExecutionContext.Implicits.global

object QueueConsumerDaemon extends com.twitter.inject.app.App {
  private val mode = flag[RunMode](
    "mode",
    "The consumer daemon to run."
  )

  override protected def modules: Seq[Module] =
    Modules() ++ Seq(new HttpClientModule)

  override protected def run(): Unit = {
    logger.info(s"Starting consumer in ${mode()} mode")

    val listener = mode() match {
      case TaskConsumer =>
        new SqsQueueListener(injector.instance[TaskQueueWorker])
      case EsIngestConsumer =>
        new SqsQueueListener(injector.instance[EsIngestQueueWorker])
      case EsItemDenormalizeConsumer =>
        new SqsQueueListener(injector.instance[EsDenormalizeItemWorker])
    }

    listener.start()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        listener.stop()
      }
    }))

    Await.ready(this)
  }
}

object RunMode {
  final private val TaskConsumerType = "TaskConsumer"
  final private val EsIngestConsumerType = "EsIngestConsumer"
  final private val EsItemDenormalizeConsumerType = "EsItemDenormalizeConsumer"

  implicit val flaggable_runMode: Flaggable[RunMode] = new Flaggable[RunMode] {
    override def parse(s: String): RunMode =
      s match {
        case TaskConsumerType              => TaskConsumer
        case EsIngestConsumerType          => EsIngestConsumer
        case EsItemDenormalizeConsumerType => EsItemDenormalizeConsumer
        case _                             => throw new IllegalArgumentException
      }
  }
}

sealed trait RunMode
case object TaskConsumer extends RunMode
case object EsIngestConsumer extends RunMode
case object EsItemDenormalizeConsumer extends RunMode
