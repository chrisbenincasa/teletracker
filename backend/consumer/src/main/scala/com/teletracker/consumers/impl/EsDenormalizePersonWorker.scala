package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase._
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueAsyncBatchWorker,
  SqsQueueWorkerConfig
}
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.elasticsearch.denorm.DenormalizedPersonUpdater
import com.teletracker.common.inject.QueueConfigAnnotations.DenormalizePersonQueueConfig
import com.teletracker.common.pubsub.EsDenormalizePersonMessage
import com.twitter.util.Stopwatch
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EsDenormalizePersonWorker @Inject()(
  queue: SqsFifoQueue[EsDenormalizePersonMessage],
  @DenormalizePersonQueueConfig
  config: ReloadableConfig[SqsQueueWorkerConfig],
  denormalizedPersonUpdater: DenormalizedPersonUpdater
)(implicit executionContext: ExecutionContext)
    extends SqsQueueAsyncBatchWorker[EsDenormalizePersonMessage](queue, config) {

  override protected def process(
    msg: Seq[EsDenormalizePersonMessage]
  ): Future[Seq[FinishedAction]] = {
    Future.sequence(msg.map(process))
  }

  private def process(
    msg: Id[EsDenormalizePersonMessage]
  ): Future[Id[FinishedAction]] = {
    val elapsed = Stopwatch.start()
    logger.info(
      s"Denormalizing person: ${msg.personId} (groupId = ${msg.message_group_id})"
    )

    denormalizedPersonUpdater
      .fullyDenormalizePerson(msg.personId, dryRun = false)
      .map(_ => {
        logger.info(
          s"Done denormalizing person: ${msg.personId}, took ${elapsed().inMillis} millis"
        )
        msg.receiptHandle.map(Ack).getOrElse(DoNothing)
      })
      .recover {
        case NonFatal(e) =>
          logger.error(
            s"Failure while attempting to denormalize person with id = ${msg.personId}.",
            e
          )
          msg.receiptHandle.map(ClearVisibility).getOrElse(DoNothing)
      }
  }
}
