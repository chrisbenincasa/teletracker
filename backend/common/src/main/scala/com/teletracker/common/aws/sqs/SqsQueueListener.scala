package com.teletracker.common.aws.sqs

import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase
import com.teletracker.common.pubsub.EventBase
import javax.inject.Inject
import org.slf4j.LoggerFactory
import scala.concurrent.Future

class SqsQueueListener[
  T <: EventBase: Manifest,
  Wrapper[_],
  RetWrapper[_]] @Inject()(
  protected val worker: SqsQueueWorkerBase[T, Wrapper, RetWrapper]) {

  protected lazy val logger = LoggerFactory.getLogger(getClass)

  private val workerThread = new Thread(new Runnable {
    override def run(): Unit = {
      worker.run()
    }
  })

  def start(): Unit = {
    synchronized {
      workerThread.setDaemon(true)
      workerThread.setName(
        s"QueueListener-Worker-${manifest[T].runtimeClass.getSimpleName}-${workerThread.getName}"
      )
      workerThread.start()
    }
  }

  def stop(): Future[Unit] = {
    synchronized {
      worker.stop()
    }
  }

  private def isWorkerThreadRunning: Boolean = workerThread.isAlive
}

case object ProcessingFailedException extends Exception
