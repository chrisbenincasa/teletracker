package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase._
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueAsyncBatchWorker,
  SqsQueueWorkerConfig
}
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.inject.QueueConfigAnnotations
import com.teletracker.common.pubsub.EsDenormalizeItemMessage
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.twitter.util.Stopwatch
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EsDenormalizeItemWorker @Inject()(
  queue: SqsFifoQueue[EsDenormalizeItemMessage],
  @QueueConfigAnnotations.DenormalizeItemQueueConfig
  config: ReloadableConfig[SqsQueueWorkerConfig],
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends SqsQueueAsyncBatchWorker[EsDenormalizeItemMessage](queue, config) {

  override protected def process(
    msg: Seq[EsDenormalizeItemMessage]
  ): Future[Seq[FinishedAction]] = {
    Future.sequence(msg.map(process))
  }

  private def process(
    msg: Id[EsDenormalizeItemMessage]
  ): Future[Id[FinishedAction]] = {
    val elapsed = Stopwatch.start()
    logger.info(
      s"Denormalizing item: ${msg.itemId} (groupId = ${msg.message_group_id})"
    )

    denormalizedItemUpdater
      .fullyDenormalizeItem(
        DenormalizeItemTaskArgs(
          itemId = msg.itemId,
          creditsChanged = msg.creditsChanged,
          crewChanged = msg.crewChanged,
          dryRun = msg.dryRun
        )
      )
      .map(_ => {
        logger.info(
          s"Done denormalizing item: ${msg.itemId}, took ${elapsed().inMillis} millis"
        )
        msg.receiptHandle.map(Ack).getOrElse(DoNothing)
      })
      .recover {
        case NonFatal(e) =>
          logger.error(
            s"Failure while attempting to denormalize item with id = ${msg.itemId}.",
            e
          )
          msg.receiptHandle.map(ClearVisibility).getOrElse(DoNothing)
      }
  }
}
