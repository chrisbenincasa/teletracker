package com.teletracker.consumers

import com.google.inject.Module
import com.teletracker.common.aws.sqs.SqsQueueListener
import com.teletracker.consumers.impl._
import com.teletracker.consumers.inject.Modules
import com.twitter.app.Flaggable
import com.twitter.inject.Injector
import com.twitter.util.Await
import scala.concurrent.ExecutionContext.Implicits.global

object QueueConsumerDaemon extends com.twitter.inject.app.App {
  val mode = flag[RunMode](
    "mode",
    "The consumer daemon to run."
  )

  override protected def modules: Seq[Module] =
    Modules()

  override protected def run(): Unit = {
    logger.info(s"Starting consumer in ${mode()} mode")

    val listener = listenerForMode(injector, mode())
    listener.start()

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        listener.stop()
      }
    }))

    Await.ready(this)
  }

  def listenerForMode(
    injector: Injector,
    runMode: RunMode
  ): SqsQueueListener[_] = {
    runMode match {
      case TaskConsumer =>
        new SqsQueueListener(injector.instance[TaskQueueWorker])
      case EsIngestConsumer =>
        new SqsQueueListener(injector.instance[EsIngestQueueWorker])
      case EsItemDenormalizeConsumer =>
        new SqsQueueListener(injector.instance[EsDenormalizeItemWorker])
      case EsPersonDenormalizeConsumer =>
        new SqsQueueListener(injector.instance[EsDenormalizePersonWorker])
      case ScrapeItemImportConsumer =>
        new SqsQueueListener(injector.instance[ScrapeItemImportWorker])
    }
  }
}

object RunMode {
  final private val TaskConsumerType = "TaskConsumer"
  final private val EsIngestConsumerType = "EsIngestConsumer"
  final private val EsItemDenormalizeConsumerType = "EsItemDenormalizeConsumer"
  final private val EsPersonDenormalizeConsumerType =
    "EsPersonDenormalizeConsumer"
  final private val ScrapeItemImportConsumerType = "ScrapeItemImportConsumer"

  implicit val flaggable_runMode: Flaggable[RunMode] = new Flaggable[RunMode] {
    override def parse(s: String): RunMode =
      s match {
        case TaskConsumerType                => TaskConsumer
        case EsIngestConsumerType            => EsIngestConsumer
        case EsItemDenormalizeConsumerType   => EsItemDenormalizeConsumer
        case EsPersonDenormalizeConsumerType => EsPersonDenormalizeConsumer
        case ScrapeItemImportConsumerType    => ScrapeItemImportConsumer
        case _                               => throw new IllegalArgumentException
      }
  }

  final val all: Seq[RunMode] = Seq(
    TaskConsumer,
    EsIngestConsumer,
    EsItemDenormalizeConsumer,
    EsPersonDenormalizeConsumer,
    ScrapeItemImportConsumer
  )
}

sealed trait RunMode
case object TaskConsumer extends RunMode
case object EsIngestConsumer extends RunMode
case object EsItemDenormalizeConsumer extends RunMode
case object EsPersonDenormalizeConsumer extends RunMode
case object ScrapeItemImportConsumer extends RunMode
