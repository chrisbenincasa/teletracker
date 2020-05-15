package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.FutureOption
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig
}
import com.teletracker.common.elasticsearch.denorm.DenormalizedItemUpdater
import com.teletracker.common.pubsub.EsDenormalizeItemMessage
import com.teletracker.common.tasks.model.DenormalizeItemTaskArgs
import com.teletracker.consumers.inject.QueueConfigAnnotations
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class EsDenormalizeItemWorker @Inject()(
  queue: SqsFifoQueue[EsDenormalizeItemMessage],
  @QueueConfigAnnotations.DenormalizeItemQueueConfig
  config: SqsQueueThroughputWorkerConfig,
  denormalizedItemUpdater: DenormalizedItemUpdater
)(implicit executionContext: ExecutionContext)
    extends SqsQueueThroughputWorker[EsDenormalizeItemMessage](queue, config) {
  override protected def process(
    msg: EsDenormalizeItemMessage
  ): FutureOption[String] = {
    logger.info(s"Denormalizing item: ${msg.itemId}")

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
        msg.receiptHandle
      })
      .recover {
        case NonFatal(e) =>
          logger.error(
            s"Failure while attempting to denormalize item with id = ${msg.itemId}.",
            e
          )
          None
      }
  }
}
