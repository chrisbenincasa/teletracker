package com.teletracker.service.process

import com.google.inject.Provider
import com.twitter.concurrent.NamedPoolThreadFactory
import javax.inject.Inject
import com.teletracker.service.util.Futures._
import java.util.concurrent.Executors
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.NonFatal

final class TmdbBackgroundProcessor @Inject()(
  queue: ProcessQueue[ProcessMessage],
  handlerProvider: Provider[TmdbMessageHandler])
    extends AutoCloseable {
  final private val pool = Executors.newFixedThreadPool(
    10,
    new NamedPoolThreadFactory("TmdbBackgroundWorker", makeDaemons = true)
  )

  final private val looper = Executors.newSingleThreadExecutor()

  @volatile private var _running = false
  @volatile private var _shutdownRequested = false

  def run(): Unit = {
    if (!_running) {
      println("Starting background processor...")
      _running = true
      looper.submit(new Runnable {
        override def run(): Unit = {
          runInternal()
        }
      })
    }
  }

  @tailrec
  private def runInternal(): Unit = {
    if (_shutdownRequested) return

    try {
      queue
        .dequeue(1)
        .await()
        .foreach(message => {
          pool.submit(new Runnable {
            override def run(): Unit = {
              handlerProvider.get().handle(message).await()
              queue.ack(message.id)
            }
          })
        })
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    }

    Thread.sleep(5000)

    runInternal()
  }

  override def close(): Unit = {
    _shutdownRequested = true
    pool.shutdown()
    looper.shutdown()
  }
}

final class TmdbMessageHandler {
  def handle(message: ProcessMessage): Future[Unit] = {
    println(s"handle message id = ${message.id}")
    Future.unit
  }
}
