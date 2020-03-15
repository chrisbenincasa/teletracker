package com.teletracker.common.aws.sqs.worker.poll

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.pubsub.EventBase
import com.teletracker.common.util.execution.ProvidedSchedulerService
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerConfig
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

trait Heartbeats[T <: EventBase] {
  protected def logger: Logger
  protected def queue: SqsQueue[T]
  protected def getConfig: SqsQueueWorkerConfig

  protected def heartbeatPool: ProvidedSchedulerService
  protected val heartbeatRegistry =
    new ConcurrentHashMap[String, Heartbeat[T]]()

  protected def registerHeartbeat(item: T): Unit = {
    val heartbeat =
      getConfig.heartbeat
        .map(
          _ =>
            new Heartbeat[T](
              item,
              queue,
              getConfig.heartbeat.get,
              scheduler = heartbeatPool
            )
        )

    heartbeat.foreach(h => {
      // if one existed already, stop it
      unregisterHeartbeat(item.receipt_handle.get)

      logger.info("Registering and starting heartbeat")

      h.start()

      heartbeatRegistry.put(item.receipt_handle.get, h)
    })
  }

  protected def unregisterHeartbeat(recieptHandle: String): Unit = {
    Option(heartbeatRegistry.remove(recieptHandle)).foreach(_.complete())
  }

  protected def clearAllHeartbeats(): Unit = {
    heartbeatRegistry.clear()
  }
}